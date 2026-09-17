<?php
/**
 * @file AdminReservationPolicyController.php
 * @brief Controlador administrativo do simulador do Motor de Políticas de Reserva.
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

/**
 * @class AdminReservationPolicyController
 * @brief Controlador de Back-Office para simulação e teste de políticas de reserva.
 * @details Estende ModuleAdminController. Atua como cliente HTTP resiliente (com timeout
 *          de 600ms e tratamento de erros RFC 7807) para o serviço Python auxiliar local na porta 8105.
 */
class AdminReservationPolicyController extends ModuleAdminController
{
    /**
     * @brief Construtor do controlador administrativo.
     */
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->override_folder = '';
    }

    /**
     * @brief Processa ações administrativas de exportação e manutenção de auditoria.
     * @return void
     */
    public function postProcess()
    {
        if (Tools::isSubmit('downloadAuditLog')) {
            $logFile = _PS_MODULE_DIR_ . $this->module->name . '/data/audit_log.json';
            $content = file_exists($logFile) ? file_get_contents($logFile) : json_encode(array(), JSON_PRETTY_PRINT);

            header('Content-Type: application/json; charset=utf-8');
            header('Content-Disposition: attachment; filename="policy_audit_log_' . date('Ymd_His') . '.json"');
            header('Content-Length: ' . strlen($content));
            header('Pragma: no-cache');
            header('Expires: 0');
            echo $content;
            exit;
        }

        if (Tools::isSubmit('clearAuditLog')) {
            $logFile = _PS_MODULE_DIR_ . $this->module->name . '/data/audit_log.json';
            if (file_exists($logFile)) {
                file_put_contents($logFile, json_encode(array(), JSON_PRETTY_PRINT));
            }
            $this->confirmations[] = $this->l('Policy audit log cleared successfully.');
        }

        parent::postProcess();
    }

    /**
     * @brief Inicializa o conteúdo da página e processa a simulação de avaliação de políticas.
     * @details Captura os parâmetros do formulário administrativo, despacha requisição síncrona
     *          via cURL com correlation ID para o serviço Python e encaminha o resultado
     *          ou mensagem de contingência para o template Smarty.
     * @return void
     * @warning Se o serviço Python na porta 8105 estiver inativo, uma mensagem de contingência é
     *          exibida sem interromper a execução do Back-Office.
     */
    public function initContent()
    {
        parent::initContent();

        $evalData     = null;
        $errorMessage = null;
        $selectedPolicy = Tools::getValue('policy_type', 'MINIMUM_STAY');

        $rawOverbookingRate = Tools::getValue('max_overbooking_rate');
        if ($rawOverbookingRate !== false && $rawOverbookingRate !== '') {
            $parsedRate = max(0.0, (float) $rawOverbookingRate);
            $normalizedOverbookingRate = $parsedRate / 100.0;
            $displayOverbookingRate = $parsedRate;
        } else {
            $normalizedOverbookingRate = 0.05;
            $displayOverbookingRate = 5;
        }

        if (Tools::isSubmit('submitPolicySimulation')) {
            $policyType = Tools::getValue('policy_type');
            $corrId     = Tools::passwdGen(16, 'ALPHANUMERIC');

            $facts = array();
            if ($policyType === 'MINIMUM_STAY') {
                $facts = array(
                    'requested_nights'        => (int) Tools::getValue('requested_nights', 1),
                    'required_minimum_nights' => (int) Tools::getValue('required_minimum_nights', 2),
                    'room_type'               => Tools::getValue('room_type', 'standard'),
                );
            } elseif ($policyType === 'ADVANCE_BOOKING') {
                $facts = array(
                    'days_in_advance'  => (int) Tools::getValue('days_in_advance', 0),
                    'min_advance_days' => (int) Tools::getValue('min_advance_days', 3),
                );
            } elseif ($policyType === 'OVERBOOKING_LIMIT') {
                $facts = array(
                    'total_capacity'       => (int) Tools::getValue('total_capacity', 50),
                    'current_occupied'     => (int) Tools::getValue('current_occupied', 0),
                    'requested_units'      => (int) Tools::getValue('requested_units', 1),
                    'max_overbooking_rate' => $normalizedOverbookingRate,
                );
            }

            $payload = json_encode(array(
                'policy' => $policyType,
                'facts'  => $facts,
            ));

            $ch = curl_init('http://127.0.0.1:8105/v1/policy-evaluations');
            curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
            curl_setopt($ch, CURLOPT_POST, true);
            curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
            curl_setopt($ch, CURLOPT_TIMEOUT_MS, 600);
            curl_setopt($ch, CURLOPT_HTTPHEADER, array(
                'Content-Type: application/json',
                'X-Correlation-ID: ' . $corrId,
            ));

            $response = curl_exec($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            $curlError = curl_error($ch);
            curl_close($ch);

            if ($response && $httpCode === 200) {
                $evalData = json_decode($response, true);
                $this->saveAuditLog($policyType, $facts, $evalData, $corrId);
            } else {
                if ($httpCode === 400 && $response) {
                    $decodedErr = json_decode($response, true);
                    $detail = isset($decodedErr['detail']) ? $decodedErr['detail'] : '';
                    $title  = isset($decodedErr['title']) ? $decodedErr['title'] : $this->l('Invalid Facts');
                    if (is_array($detail)) {
                        $errorMessage = $title . ': ' . json_encode($detail);
                    } elseif ($detail) {
                        $errorMessage = $title . ': ' . $detail;
                    } else {
                        $errorMessage = $this->l('Invalid request (HTTP 400).');
                    }
                } elseif ($httpCode === 422 && $response) {
                    $decodedErr = json_decode($response, true);
                    $detail = isset($decodedErr['detail']) ? $decodedErr['detail'] : '';
                    $errorMsgs = array();
                    if (is_array($detail)) {
                        foreach ($detail as $err) {
                            if (is_array($err) && isset($err['msg'])) {
                                $loc = isset($err['loc']) && is_array($err['loc']) ? end($err['loc']) : '';
                                $errorMsgs[] = ($loc ? $loc . ': ' : '') . $err['msg'];
                            }
                        }
                    }
                    $errorMessage = !empty($errorMsgs)
                        ? $this->l('Validation error (HTTP 422): ') . implode('; ', $errorMsgs)
                        : $this->l('Invalid policy or parameter (HTTP 422).');
                } else {
                    $errorMessage = sprintf(
                        $this->l('Local policy service offline or unavailable (HTTP %d). Ensure the Python server is running on port 8105.'),
                        (int) $httpCode
                    );
                }
            }
        }

        $logFile = _PS_MODULE_DIR_ . $this->module->name . '/data/audit_log.json';
        $auditLogs = array();
        if (file_exists($logFile)) {
            $rawContent = file_get_contents($logFile);
            $decoded = json_decode($rawContent, true);
            if (is_array($decoded)) {
                $auditLogs = $decoded;
            }
        }

        $formattedRecentLogs = array();
        $recentLogs = array_slice($auditLogs, 0, 5);
        foreach ($recentLogs as $log) {
            $logCopy = $log;
            $logCopy['facts_json'] = json_encode(isset($log['facts']) ? $log['facts'] : array(), JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
            $formattedRecentLogs[] = $logCopy;
        }

        $this->context->smarty->assign(array(
            'policyEvaluation' => $evalData,
            'policyError'      => $errorMessage,
            'selectedPolicy'   => $selectedPolicy,
            'auditLogs'        => $formattedRecentLogs,
            'totalAuditLogs'   => count($auditLogs),
            'currentValues'    => array(
                'requested_nights'        => (int) Tools::getValue('requested_nights', 1),
                'required_minimum_nights' => (int) Tools::getValue('required_minimum_nights', 2),
                'room_type'               => Tools::getValue('room_type', 'standard'),
                'days_in_advance'         => (int) Tools::getValue('days_in_advance', 0),
                'min_advance_days'        => (int) Tools::getValue('min_advance_days', 3),
                'total_capacity'          => (int) Tools::getValue('total_capacity', 50),
                'current_occupied'        => (int) Tools::getValue('current_occupied', 0),
                'requested_units'         => (int) Tools::getValue('requested_units', 1),
                'max_overbooking_rate'    => $displayOverbookingRate,
            ),
        ));

        // Renderiza o template do simulador no painel administrativo
        $templatePath = _PS_MODULE_DIR_ . $this->module->name . '/views/templates/admin/policy_simulator.tpl';
        if (file_exists($templatePath)) {
            $this->content = $this->context->smarty->createTemplate($templatePath, $this->context->smarty)->fetch();
        } else {
            $this->content = $this->createTemplate('policy_simulator.tpl')->fetch();
        }
        $this->context->smarty->assign('content', $this->content);
    }

    /**
     * @brief Registra um evento de avaliação de política no arquivo audit_log.json.
     * @param string $policyType Identificador da política avaliada
     * @param array $facts Conjunto de fatos contextuais enviados ao motor
     * @param array $evalData Resposta retornada pelo serviço de políticas
     * @param string $corrId Identificador de correlação da requisição
     * @return void
     */
    protected function saveAuditLog($policyType, array $facts, array $evalData, $corrId)
    {
        $dataDir = _PS_MODULE_DIR_ . $this->module->name . '/data';
        if (!is_dir($dataDir)) {
            @mkdir($dataDir, 0755, true);
            @file_put_contents(
                $dataDir . '/index.php',
                "<?php\nheader('Expires: Mon, 26 Jul 1997 05:00:00 GMT');\nheader('Last-Modified: ' . gmdate('D, d M Y H:i:s') . ' GMT');\nheader('Cache-Control: no-store, no-cache, must-revalidate');\nheader('Location: ../');\nexit;\n"
            );
        }

        $logFile = $dataDir . '/audit_log.json';
        $auditLogs = array();

        if (file_exists($logFile)) {
            $raw = file_get_contents($logFile);
            $decoded = json_decode($raw, true);
            if (is_array($decoded)) {
                $auditLogs = $decoded;
            }
        }

        $entry = array(
            'timestamp'      => date('Y-m-d H:i:s'),
            'correlation_id' => isset($evalData['correlation_id']) ? $evalData['correlation_id'] : $corrId,
            'policy'         => $policyType,
            'facts'          => $facts,
            'decision'       => isset($evalData['decision']) ? $evalData['decision'] : '',
            'reason_code'    => isset($evalData['reason_code']) ? $evalData['reason_code'] : '',
            'explanation'    => isset($evalData['explanation']) ? $evalData['explanation'] : '',
        );

        array_unshift($auditLogs, $entry);

        // Limita o histórico persistido em arquivo aos últimos 100 registros
        if (count($auditLogs) > 100) {
            $auditLogs = array_slice($auditLogs, 0, 100);
        }

        file_put_contents($logFile, json_encode($auditLogs, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
    }
}
