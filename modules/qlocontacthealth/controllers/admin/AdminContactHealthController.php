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

require_once dirname(__FILE__) . '/../../classes/QloContactHealthCustomer.php';

class AdminContactHealthController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        $this->table = 'customer';
        $this->className = 'Customer';
        $this->lang = false;
        $this->deleted = false;
        $this->identifier = 'id_customer';
        $this->_defaultOrderBy = 'id_customer';
        $this->_defaultOrderWay = 'DESC';
        $this->list_no_link = true;

        parent::__construct();

        $this->fields_list = array(
            'id_customer' => array(
                'title' => $this->l('ID'),
                'align' => 'text-center',
                'class' => 'fixed-width-xs',
            ),
            'firstname' => array(
                'title' => $this->l('Nome'),
                'filter_key' => 'a!firstname',
            ),
            'lastname' => array(
                'title' => $this->l('Sobrenome'),
                'filter_key' => 'a!lastname',
            ),
            'email' => array(
                'title' => $this->l('E-mail'),
                'filter_key' => 'a!email',
            ),
            'date_upd' => array(
                'title' => $this->l('Última Atualização'),
                'type' => 'datetime',
                'filter_key' => 'a!date_upd',
            ),
        );

        $this->addRowAction('view');
    }

    /**
     * Initializes controller content based on view display or customer list.
     */
    public function initContent()
    {
        $customerId = (int) Tools::getValue('id_customer');
        if ($customerId || Tools::isSubmit('viewcustomer')) {
            $this->display = 'view';
        }

        parent::initContent();
    }

    /**
     * Renders the contact health card view for the given customer ID.
     *
     * @return string
     */
    public function renderView()
    {
        $customerId = (int) Tools::getValue('id_customer');
        if (!$customerId) {
            $this->errors[] = $this->l('ID do cliente não informado.');
            return parent::renderView();
        }

        $backUrl = $this->context->link->getAdminLink('AdminContactHealth');
        $backButton = '<div class="panel-footer" style="margin-top: 15px;">'
            . '<a href="' . htmlspecialchars($backUrl, ENT_QUOTES, 'UTF-8') . '" class="btn btn-default">'
            . '<i class="process-icon-back"></i> ' . $this->l('Voltar para a Lista de Clientes')
            . '</a></div>';

        return $this->module->renderContactHealthCard($customerId) . $backButton;
    }

    /**
     * AJAX action to simulate contact reconfirmation challenge and create an audit event.
     */
    public function ajaxProcessSimulateReconfirmation()
    {
        $customerId = (int) Tools::getValue('id_customer');
        if (!$customerId) {
            die(json_encode(array(
                'success' => false,
                'message' => $this->l('ID do cliente não informado.'),
            )));
        }

        $customer = new Customer($customerId);
        if (!Validate::isLoadedObject($customer)) {
            die(json_encode(array(
                'success' => false,
                'message' => $this->l('Cliente não encontrado.'),
            )));
        }

        // 1. Log audit event in QloApps Logger
        Logger::addLog(
            sprintf('[qlocontacthealth] Simulação de desafio de reconfirmação de contato gerada para o cliente ID: %d', $customerId),
            1,
            null,
            'Customer',
            $customerId,
            true
        );

        // 2. Update persistent verification date in DB
        QloContactHealthCustomer::recordVerification($customerId);

        // 3. Return JSON response
        die(json_encode(array(
            'success' => true,
            'message' => sprintf(
                $this->l('Evento auditável de envio de token de verificação registrado com sucesso para o cliente ID #%d (sem disparo de mensagens de rede reais em ambiente local).'),
                $customerId
            ),
        )));
    }
}
