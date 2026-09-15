<?php

/**
 * NOTICE OF LICENSE
 *
 * This source file is subject to the Academic Free License (AFL 3.0)
 * that is bundled with this package in the file LICENSE.txt.
 *
 * @author    QloApps Engineering
 * @copyright Since 2010 QloApps
 * @license   http://opensource.org/licenses/afl-3.0.php Academic Free License (AFL 3.0)
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

require_once _PS_MODULE_DIR_ . 'qloexternalrequests/classes/ExternalRequestsClient.php';

class AdminExternalRequestsController extends ModuleAdminController
{
    /**
     * @var ExternalRequestsClient
     */
    private $apiClient;

    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->toolbar_title = $this->l('External Channel Converter');
        $this->apiClient = new ExternalRequestsClient();
    }

    /**
     * @return void
     */
    public function initContent()
    {
        parent::initContent();

        $selectedProvider = (string) Tools::getValue('provider_code', 'PROVIDER_A');
        $rawPayloadJson = (string) Tools::getValue('raw_payload_json', '');
        $conversionResult = null;
        $conversionError = null;

        if (Tools::isSubmit('submitConvertRequest')) {
            $processOutcome = $this->processConvertRequest(
                $selectedProvider,
                $rawPayloadJson
            );
            $conversionResult = $processOutcome['conversionResult'];
            $conversionError = $processOutcome['conversionError'];
        }

        $this->context->smarty->assign([
            'selectedProvider' => $selectedProvider,
            'rawPayloadJson' => $rawPayloadJson,
            'conversionResult' => $conversionResult,
            'conversionError' => $conversionError,
            'actionUrl' => self::$currentIndex . '&token=' . $this->token,
        ]);

        $this->content .= $this->context->smarty->fetch(
            _PS_MODULE_DIR_ . 'qloexternalrequests/views/templates/admin/request_converter.tpl'
        );
        $this->context->smarty->assign('content', $this->content);
    }

    /**
     * Processes form submission and invokes the conversion client.
     *
     * @param string $provider
     * @param string $rawJson
     * @return array{conversionResult: array<string, mixed>|null, conversionError: string|null}
     */
    private function processConvertRequest(string $provider, string $rawJson): array
    {
        $trimmedJson = trim($rawJson);
        if ($trimmedJson === '') {
            return [
                'conversionResult' => null,
                'conversionError' => $this->l('JSON payload cannot be empty.'),
            ];
        }

        $parsedPayload = json_decode($trimmedJson, true);
        if (!is_array($parsedPayload)) {
            return [
                'conversionResult' => null,
                'conversionError' => $this->l('Invalid or malformed JSON input payload.'),
            ];
        }

        /** @var array<string, mixed> $payloadArray */
        $payloadArray = $parsedPayload;

        $callResult = $this->apiClient->convert($provider, $payloadArray);
        if ($callResult['http_code'] === 200 || $callResult['http_code'] === 400) {
            $data = is_array($callResult['data']) ? $this->normalizeErrorMessages($callResult['data']) : null;
            $error = null;
            if ($data === null && !empty($callResult['error'])) {
                $error = $this->l((string) $callResult['error']);
            }

            return [
                'conversionResult' => $data,
                'conversionError' => $error,
            ];
        }

        return [
            'conversionResult' => null,
            'conversionError' => $this->l((string) $callResult['error']),
        ];
    }

    /**
     * Normalizes and translates validation error messages to English.
     *
     * @param array<string, mixed> $result
     * @return array<string, mixed>
     */
    private function normalizeErrorMessages(array $result): array
    {
        if (empty($result['errors']) || !is_array($result['errors'])) {
            return $result;
        }

        $translations = [
            'Nome do hóspede não pode ser vazio.' => 'Guest name cannot be empty.',
            'A quantidade de noites deve ser maior ou igual a 1.' => 'The number of nights must be greater than or equal to 1.',
            'Campo obrigatório \'guest_full_name\' não encontrado no payload do PROVIDER_A.' => 'Required field \'guest_full_name\' not found in PROVIDER_A payload.',
            'Campo obrigatório \'arrival\' não encontrado no payload do PROVIDER_A.' => 'Required field \'arrival\' not found in PROVIDER_A payload.',
            'Campo obrigatório \'nights\' não encontrado no payload do PROVIDER_A.' => 'Required field \'nights\' not found in PROVIDER_A payload.',
            'Campo \'nights\' deve ser um número inteiro.' => 'Field \'nights\' must be an integer.',
            'Objeto obrigatório \'customer\' não encontrado no payload do PROVIDER_B.' => 'Required object \'customer\' not found in PROVIDER_B payload.',
            'Campo obrigatório \'first_name\' não encontrado ou vazio.' => 'Required field \'first_name\' not found or empty.',
            'Campo obrigatório \'last_name\' não encontrado ou vazio.' => 'Required field \'last_name\' not found or empty.',
            'Campo obrigatório \'checkin_date\' não encontrado no payload do PROVIDER_B.' => 'Required field \'checkin_date\' not found in PROVIDER_B payload.',
            'Campo obrigatório \'checkout_date\' não encontrado no payload do PROVIDER_B.' => 'Required field \'checkout_date\' not found in PROVIDER_B payload.',
        ];

        /** @var array<int, array<string, mixed>> $errorsList */
        $errorsList = $result['errors'];
        foreach ($errorsList as $key => $err) {
            if (empty($err['message'])) {
                continue;
            }
            $msg = (string) $err['message'];
            if (isset($translations[$msg])) {
                $errorsList[$key]['message'] = $translations[$msg];
            } elseif (strpos($msg, 'Formato de data inválido para') !== false) {
                $errorsList[$key]['message'] = str_replace(
                    'Formato de data inválido para',
                    'Invalid date format for',
                    $msg
                );
            } elseif (strpos($msg, 'deve ser posterior à data de check-in') !== false) {
                $errorsList[$key]['message'] = (string) preg_replace(
                    '/Data de check-out \((.*?)\) deve ser posterior à data de check-in \((.*?)\)\./',
                    'Check-out date ($1) must be after check-in date ($2).',
                    $msg
                );
            }
        }
        $result['errors'] = $errorsList;

        return $result;
    }
}
