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
            $data = is_array($callResult['data']) ? $callResult['data'] : null;
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
}
