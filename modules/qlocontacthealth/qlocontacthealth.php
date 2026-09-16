<?php
/**
 * 2010-2026 QloApps
 *
 * NOTICE OF LICENSE
 *
 * This source file is subject to the Academic Free License (AFL 3.0)
 * that is bundled with this package in the file LICENSE.txt.
 * It is also available through the world-wide-web at this URL:
 * http://opensource.org/licenses/afl-3.0.php
 *
 * @author    QloApps Engineering <support@qloapps.com>
 * @copyright 2010-2026 QloApps
 * @license   http://opensource.org/licenses/afl-3.0.php Academic Free License (AFL 3.0)
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

require_once dirname(__FILE__) . '/classes/QloContactHealthCustomer.php';

class QloContactHealth extends Module
{
    const CONFIG_API_URL = 'QLOCONTACTHEALTH_API_URL';
    const CONFIG_API_TIMEOUT = 'QLOCONTACTHEALTH_API_TIMEOUT';
    const DEFAULT_API_URL = 'http://127.0.0.1:8103/v1/contact-evaluations';
    const DEFAULT_TIMEOUT_MS = 600;

    public function __construct()
    {
        $this->name = 'qlocontacthealth';
        $this->tab = 'administration';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Avaliador de Saúde de Contatos');
        $this->description = $this->l('Métricas de higiene, staleness e conformidade de contatos de hóspedes.');
    }

    /**
     * Installs module, database table, tab, configuration and registers hooks.
     *
     * @return bool
     */
    public function install()
    {
        if (!parent::install()
            || !QloContactHealthCustomer::createTable()
            || !$this->installTab()
            || !$this->registerHook('displayAdminCustomers')
        ) {
            return false;
        }

        Configuration::updateValue(self::CONFIG_API_URL, self::DEFAULT_API_URL);
        Configuration::updateValue(self::CONFIG_API_TIMEOUT, self::DEFAULT_TIMEOUT_MS);

        return true;
    }

    /**
     * Uninstalls module, tab, database table and configuration.
     *
     * @return bool
     */
    public function uninstall()
    {
        Configuration::deleteByName(self::CONFIG_API_URL);
        Configuration::deleteByName(self::CONFIG_API_TIMEOUT);

        return QloContactHealthCustomer::dropTable()
            && $this->uninstallTab()
            && parent::uninstall();
    }

    /**
     * Installs admin tab under AdminParentCustomer menu.
     *
     * @return bool
     */
    private function installTab()
    {
        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminContactHealth';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Saúde de Contatos';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentCustomer');
        $tab->module = $this->name;

        return (bool) $tab->add();
    }

    /**
     * Uninstalls admin tab.
     *
     * @return bool
     */
    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminContactHealth');
        if ($idTab) {
            $tab = new Tab($idTab);
            return (bool) $tab->delete();
        }

        return true;
    }

    /**
     * Hook displayed on admin customer profile view.
     *
     * @param array $params Hook parameters containing id_customer
     * @return string
     */
    public function hookDisplayAdminCustomers($params)
    {
        $customerId = isset($params['id_customer']) ? (int) $params['id_customer'] : 0;
        if (!$customerId) {
            return '';
        }

        return $this->renderContactHealthCard($customerId);
    }

    /**
     * Generates an RFC 4122 compliant UUIDv4 string.
     *
     * @return string
     */
    public static function generateUuidV4()
    {
        $data = random_bytes(16);
        $data[6] = chr(ord($data[6]) & 0x0f | 0x40); // set version to 0100 (4)
        $data[8] = chr(ord($data[8]) & 0x3f | 0x80); // set bits 6-7 to 10

        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
    }

    /**
     * Formats a database datetime string into ISO-8601 UTC format, returning null if empty or invalid.
     *
     * @param string|null $dateStr
     * @return string|null
     */
    public static function formatIsoDate($dateStr)
    {
        if (empty($dateStr) || $dateStr === '0000-00-00 00:00:00') {
            return null;
        }

        return date('Y-m-d\TH:i:s\Z', strtotime($dateStr));
    }

    /**
     * Evaluates customer contact health against the Kotlin microservice.
     *
     * @param int $customerId
     * @return array
     */
    public function evaluateCustomerContactHealth($customerId)
    {
        $customer = new Customer((int) $customerId);
        if (!Validate::isLoadedObject($customer)) {
            return array(
                'success' => false,
                'error' => $this->l('Cliente não encontrado.'),
            );
        }

        // Resolver telefone através do endereço principal ativo (ps_address) conforme RFC seção 5.1
        $phone = '';
        $idAddress = (int) Address::getFirstCustomerAddressId($customer->id, true);
        if ($idAddress) {
            $address = new Address($idAddress);
            if (Validate::isLoadedObject($address)) {
                $phone = !empty($address->phone_mobile) ? $address->phone_mobile : $address->phone;
            }
        }
        if (empty($phone) && !empty($customer->phone)) {
            $phone = $customer->phone;
        }
        $cleanPhone = trim((string) $phone);

        $corrId = self::generateUuidV4();
        $apiUrl = Configuration::get(self::CONFIG_API_URL) ?: self::DEFAULT_API_URL;
        $configuredTimeout = (int) Configuration::get(self::CONFIG_API_TIMEOUT);
        $timeoutMs = ($configuredTimeout > 0)
            ? min(max(1, $configuredTimeout), self::DEFAULT_TIMEOUT_MS)
            : self::DEFAULT_TIMEOUT_MS;

        // Recupera registro persistido de saúde/verificação do cliente conforme RFC-003 / RN-003 / RN-005
        $healthRecord = QloContactHealthCustomer::getByCustomerId($customer->id);

        $lastVerifiedAt   = self::formatIsoDate($healthRecord['last_verified_at'] ?? null);
        $consentExpiresAt = self::formatIsoDate($healthRecord['consent_expires_at'] ?? null);

        $refDate = date('Y-m-d');

        $payload = json_encode(array(
            'customer_id' => 'cust-' . (int) $customer->id,
            'email' => (string) $customer->email,
            'phone' => $cleanPhone,
            'last_verified_at' => $lastVerifiedAt,
            'consent_expires_at' => $consentExpiresAt,
            'reference_date' => $refDate,
        ));

        if ($payload === false) {
            return array(
                'success' => false,
                'error' => $this->l('Indicadores de saúde de contato indisponíveis no momento.'),
            );
        }

        $ch = curl_init($apiUrl);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
        curl_setopt($ch, CURLOPT_TIMEOUT_MS, $timeoutMs);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT_MS, min(300, $timeoutMs));
        curl_setopt($ch, CURLOPT_HTTPHEADER, array(
            'Content-Type: application/json',
            'X-Correlation-ID: ' . $corrId,
        ));

        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($response && $httpCode === 200) {
            $data = json_decode($response, true);
            if (json_last_error() === JSON_ERROR_NONE && is_array($data) && isset($data['overall_status'])) {
                return array(
                    'success' => true,
                    'data' => $data,
                );
            }
        }

        return array(
            'success' => false,
            'error' => $this->l('Indicadores de saúde de contato indisponíveis no momento.'),
        );
    }

    /**
     * Renders contact health card template for a customer.
     *
     * @param int $customerId
     * @return string
     */
    public function renderContactHealthCard($customerId)
    {
        $evaluation = $this->evaluateCustomerContactHealth($customerId);

        if ($evaluation['success']) {
            $this->context->smarty->assign('contactHealth', $evaluation['data']);
            $this->context->smarty->assign('healthWarning', null);
        } else {
            $this->context->smarty->assign('contactHealth', null);
            $this->context->smarty->assign('healthWarning', $evaluation['error']);
        }

        $adminLink = $this->context->link->getAdminLink('AdminContactHealth');
        $this->context->smarty->assign(array(
            'customerId' => (int) $customerId,
            'ajaxUrl' => $adminLink,
            'ajaxToken' => Tools::getAdminTokenLite('AdminContactHealth'),
        ));

        return $this->display(__FILE__, 'views/templates/admin/contact_health_card.tpl');
    }
}
