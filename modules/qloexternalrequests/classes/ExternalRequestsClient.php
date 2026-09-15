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

class ExternalRequestsClient
{
    public const DEFAULT_SERVICE_URL = 'http://127.0.0.1:8106/v1/external-reservation-requests/convert';
    public const DEFAULT_TIMEOUT_MS = 600;

    /**
     * @var string
     */
    private $serviceUrl;

    /**
     * @var int
     */
    private $timeoutMs;

    /**
     * @param string|null $serviceUrl
     * @param int|null $timeoutMs
     */
    public function __construct(?string $serviceUrl = null, ?int $timeoutMs = null)
    {
        $this->serviceUrl = $serviceUrl ?: self::DEFAULT_SERVICE_URL;
        $this->timeoutMs = $timeoutMs !== null ? $timeoutMs : self::DEFAULT_TIMEOUT_MS;
    }

    /**
     * Sends the conversion request to the canonical converter microservice.
     *
     * @param string $provider Channel provider code (e.g. PROVIDER_A, PROVIDER_B)
     * @param array<string, mixed> $payload Raw partner payload
     * @param string|null $correlationId Tracing correlation identifier
     * @return array<string, mixed> Result array containing 'success', 'http_code', 'error', and 'data'
     */
    public function convert(string $provider, array $payload, ?string $correlationId = null): array
    {
        $traceId = $correlationId ?: self::generateUuidV4();
        $requestBody = json_encode([
            'provider' => $provider,
            'payload' => empty($payload) ? new stdClass() : $payload,
        ]);

        if ($requestBody === false) {
            return [
                'success' => false,
                'http_code' => 400,
                'error' => 'Failed to encode request payload into JSON.',
                'data' => null,
            ];
        }

        return $this->executePost($requestBody, $traceId);
    }

    /**
     * Executes the HTTP POST request via cURL with a strict timeout.
     *
     * @param string $requestBody
     * @param string $correlationId
     * @return array<string, mixed>
     */
    private function executePost(string $requestBody, string $correlationId): array
    {
        $curlHandle = curl_init($this->serviceUrl);
        if ($curlHandle === false) {
            return [
                'success' => false,
                'http_code' => 500,
                'error' => 'Failed to initialize cURL handle.',
                'data' => null,
            ];
        }

        curl_setopt($curlHandle, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($curlHandle, CURLOPT_POST, true);
        curl_setopt($curlHandle, CURLOPT_POSTFIELDS, $requestBody);
        curl_setopt($curlHandle, CURLOPT_TIMEOUT_MS, $this->timeoutMs);
        curl_setopt($curlHandle, CURLOPT_CONNECTTIMEOUT_MS, $this->timeoutMs);
        curl_setopt($curlHandle, CURLOPT_HTTPHEADER, [
            'Content-Type: application/json',
            'X-Correlation-ID: ' . $correlationId,
        ]);

        $response = curl_exec($curlHandle);
        $curlErrorNo = (int) curl_errno($curlHandle);
        $curlErrorMsg = (string) curl_error($curlHandle);
        $httpCode = (int) curl_getinfo($curlHandle, CURLINFO_HTTP_CODE);
        curl_close($curlHandle);

        if ($curlErrorNo !== 0) {
            return $this->handleCurlError($curlErrorNo, $curlErrorMsg);
        }

        return $this->handleHttpResponse($httpCode, is_string($response) ? $response : '');
    }

    /**
     * Handles transport-level cURL errors (timeouts, connection failures).
     *
     * @param int $errorNumber
     * @param string $errorMessage
     * @return array<string, mixed>
     */
    private function handleCurlError(int $errorNumber, string $errorMessage): array
    {
        if ($errorNumber === CURLE_OPERATION_TIMEDOUT) {
            return [
                'success' => false,
                'http_code' => 504,
                'error' => 'Conversion service timed out after ' . $this->timeoutMs . 'ms.',
                'data' => null,
            ];
        }

        if ($errorNumber === CURLE_COULDNT_CONNECT) {
            return [
                'success' => false,
                'http_code' => 503,
                'error' => 'Canonical conversion service is offline or unreachable on port 8106.',
                'data' => null,
            ];
        }

        return [
            'success' => false,
            'http_code' => 500,
            'error' => 'Transport error connecting to conversion service: ' . $errorMessage,
            'data' => null,
        ];
    }

    /**
     * Handles HTTP response status codes.
     *
     * @param int $httpCode
     * @param string $rawResponse
     * @return array<string, mixed>
     */
    private function handleHttpResponse(int $httpCode, string $rawResponse): array
    {
        $decoded = json_decode($rawResponse, true);

        if ($httpCode === 200) {
            return [
                'success' => true,
                'http_code' => 200,
                'error' => null,
                'data' => is_array($decoded) ? $decoded : [],
            ];
        }

        if ($httpCode === 400) {
            return [
                'success' => false,
                'http_code' => 400,
                'error' => 'Conversion validation failed (Bad Request).',
                'data' => is_array($decoded) ? $decoded : [],
            ];
        }

        if ($httpCode === 503) {
            return [
                'success' => false,
                'http_code' => 503,
                'error' => 'Canonical conversion service is temporarily unavailable (HTTP 503).',
                'data' => is_array($decoded) ? $decoded : null,
            ];
        }

        return [
            'success' => false,
            'http_code' => $httpCode,
            'error' => 'Unexpected response from conversion service (HTTP ' . $httpCode . ').',
            'data' => is_array($decoded) ? $decoded : null,
        ];
    }

    /**
     * Generates a RFC 4122 compliant version 4 UUID.
     *
     * @return string
     */
    public static function generateUuidV4(): string
    {
        $data = random_bytes(16);
        $data[6] = chr((ord($data[6]) & 0x0f) | 0x40);
        $data[8] = chr((ord($data[8]) & 0x3f) | 0x80);

        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
    }
}
