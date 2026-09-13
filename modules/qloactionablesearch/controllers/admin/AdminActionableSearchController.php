<?php
/**
 * Admin Controller for Actionable Entity Search (QLO-FEAT-008)
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class AdminActionableSearchController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->page_header_toolbar_title = $this->l('Actionable Entity Search');
    }

    public function initContent()
    {
        parent::initContent();

        $searchResult = null;
        $searchError = null;
        $userQuery = '';

        // Catalogo de teste padrao da RFC-008
        $catalog = $this->getCatalogData();

        if (Tools::isSubmit('submitSearchQuery')) {
            $userQuery = trim((string) Tools::getValue('search_query'));

            if (empty($userQuery)) {
                $searchError = $this->l('Please enter at least one search term.');
            } else {
                $correlationId = Tools::passwdGen(16, 'ALPHANUMERIC');

                $payload = json_encode(array(
                    'query' => $userQuery,
                    'catalog_version' => 'v1-demo',
                    'catalog' => $catalog,
                ));

                $response = $this->querySearchService($payload, $correlationId, $httpCode, $curlError);

                if ($response && $httpCode === 200) {
                    $searchResult = json_decode($response, true);
                } elseif ($httpCode === 400 && $response) {
                    $errData = json_decode($response, true);
                    $detail = isset($errData['detail']) ? $errData['detail'] : $this->l('Invalid search query.');
                    $searchError = $detail;
                } else {
                    $searchError = $this->l('C++ search engine is offline or currently unavailable.') . ' ' .
                        $this->l('Connection failed at 127.0.0.1:8108 (max timeout of 600ms).');
                    if ($curlError) {
                        $searchError .= ' (' . $curlError . ')';
                    }
                }
            }
        }

        $this->context->smarty->assign(array(
            'searchQuery' => $userQuery,
            'searchResult' => $searchResult,
            'searchError' => $searchError,
            'catalogItems' => $catalog,
        ));

        $output = $this->context->smarty->fetch(
            _PS_MODULE_DIR_ . $this->module->name . '/views/templates/admin/search_dashboard.tpl'
        );

        $this->content .= $output;
        $this->context->smarty->assign('content', $this->content);
    }

    /**
     * Executa a chamada HTTP síncrona ao microsserviço C++
     */
    private function querySearchService($payload, $correlationId, &$httpCode, &$curlError)
    {
        // Tenta a URL configurada ou endpoints de fallback (Docker host e localhost)
        $configuredUrl = Configuration::get('QLO_ACTIONABLE_SEARCH_URL');
        $urlsToTry = array();

        if (!empty($configuredUrl)) {
            $urlsToTry[] = $configuredUrl;
        }
        $urlsToTry[] = 'http://host.docker.internal:8108/v1/search/parse';
        $urlsToTry[] = 'http://127.0.0.1:8108/v1/search/parse';
        $urlsToTry = array_unique($urlsToTry);

        $response = false;
        $httpCode = 0;
        $curlError = '';

        foreach ($urlsToTry as $url) {
            $ch = curl_init($url);
            curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
            curl_setopt($ch, CURLOPT_POST, true);
            curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
            curl_setopt($ch, CURLOPT_TIMEOUT_MS, 600); // SLA estrito de 600ms
            curl_setopt($ch, CURLOPT_CONNECTTIMEOUT_MS, 300);
            curl_setopt($ch, CURLOPT_HTTPHEADER, array(
                'Content-Type: application/json',
                'X-Correlation-ID: ' . $correlationId,
            ));

            $response = curl_exec($ch);
            $httpCode = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
            $errNo = curl_errno($ch);
            $curlError = curl_error($ch);
            curl_close($ch);

            if ($errNo === 0 && $httpCode > 0) {
                // Conexao bem-sucedida com o servico
                break;
            }
        }

        return $response;
    }

    /**
     * Retorna o catalogo de quartos com dados estruturados para a busca
     */
    private function getCatalogData()
    {
        // Fixture padrao conforme especificacao da RFC-008
        return array(
            array(
                'id' => 'room-suite-01',
                'type' => 'ROOM_TYPE',
                'title' => 'Suíte Master Vista Mar',
                'capacity_adults' => 2,
                'amenities' => array('vista_mar', 'ar_condicionado', 'banheira'),
                'aliases' => array('suite', 'suite master', 'vista mar'),
            ),
            array(
                'id' => 'room-std-02',
                'type' => 'ROOM_TYPE',
                'title' => 'Quarto Standard Casal',
                'capacity_adults' => 2,
                'amenities' => array('ar_condicionado'),
                'aliases' => array('standard', 'casal'),
            ),
            array(
                'id' => 'room-sgl-03',
                'type' => 'ROOM_TYPE',
                'title' => 'Quarto Single Individual',
                'capacity_adults' => 1,
                'amenities' => array('ventilador'),
                'aliases' => array('single', 'solteiro'),
            ),
        );
    }
}
