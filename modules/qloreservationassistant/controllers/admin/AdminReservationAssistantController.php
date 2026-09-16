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
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class AdminReservationAssistantController extends ModuleAdminController
{
    const SERVICE_URL = 'http://127.0.0.1:8101/v1/assist/interpret';
    const TIMEOUT_MS = 600;

    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->meta_title = $this->l('Copiloto de Consulta de Reservas');
    }

    public function initContent()
    {
        parent::initContent();

        $resultData = null;
        $errorMessage = null;
        $userQuery = '';
        $refDate = Tools::getValue('reference_date', date('Y-m-d'));

        // Gestão do histórico de auditoria da sessão
        $history = array();
        if (isset($this->context->cookie->qlo_assistant_history)) {
            $decoded = json_decode($this->context->cookie->qlo_assistant_history, true);
            if (is_array($decoded)) {
                $history = $decoded;
            }
        }

        if (Tools::isSubmit('submitClearHistory')) {
            unset($this->context->cookie->qlo_assistant_history);
            $history = array();
        } elseif (Tools::isSubmit('submitQueryAssistant')) {
            $userQuery = trim((string) Tools::getValue('user_query'));
            $corrId = 'req-' . Tools::passwdGen(12, 'ALPHANUMERIC');

            if (empty($userQuery)) {
                $errorMessage = $this->l('Por favor, digite a pergunta do hóspede antes de consultar.');
            } elseif (Tools::strlen($userQuery) > 256) {
                $errorMessage = $this->l('A consulta excede o limite máximo de 256 caracteres.');
            } else {
                $payload = json_encode(array(
                    'query' => $userQuery,
                    'reference_date' => $refDate,
                    'locale' => 'pt-BR'
                ));

                $ch = curl_init(self::SERVICE_URL);
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_POST, true);
                curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
                curl_setopt($ch, CURLOPT_TIMEOUT_MS, self::TIMEOUT_MS);
                curl_setopt($ch, CURLOPT_CONNECTTIMEOUT_MS, self::TIMEOUT_MS);
                curl_setopt($ch, CURLOPT_HTTPHEADER, array(
                    'Content-Type: application/json; charset=utf-8',
                    'X-Correlation-ID: ' . $corrId
                ));

                $response = curl_exec($ch);
                $curlError = curl_errno($ch);
                $httpCode = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);

                if ($curlError === 0 && $httpCode === 200 && $response) {
                    $resultData = json_decode($response, true);
                    if (is_array($resultData)) {
                        // Registra no histórico de auditoria (últimas 5 consultas)
                        array_unshift($history, array(
                            'time' => date('H:i:s'),
                            'query' => $userQuery,
                            'intent' => isset($resultData['intent']) ? $resultData['intent'] : 'UNKNOWN',
                            'confidence' => isset($resultData['confidence']) ? $resultData['confidence'] : 0,
                            'correlation_id' => $corrId
                        ));
                        $history = array_slice($history, 0, 5);
                        $this->context->cookie->qlo_assistant_history = json_encode($history);
                    } else {
                        $errorMessage = $this->l('Falha ao decodificar a resposta JSON do assistente.');
                    }
                } elseif ($httpCode === 400 || $httpCode === 415) {
                    $prob = json_decode((string) $response, true);
                    $detail = (isset($prob['detail']) && !empty($prob['detail'])) ? $prob['detail'] : $this->l('Parâmetros inválidos.');
                    $errorMessage = sprintf($this->l('Erro de validação (HTTP %d): %s'), $httpCode, $detail);
                } else {
                    // Degradação graciosa quando o C++ está offline ou timeout de 600ms excedido
                    $errorMessage = $this->l('Serviço de inferência local indisponível (HTTP 503). O Copiloto está operando em modo de contingência.');
                }
            }
        }

        $this->context->smarty->assign(array(
            'assistantResult' => $resultData,
            'assistantError' => $errorMessage,
            'currentRefDate' => $refDate,
            'lastUserQuery' => $userQuery,
            'queryHistory' => $history
        ));

        $this->setTemplate('assistant_view.tpl');
    }
}
