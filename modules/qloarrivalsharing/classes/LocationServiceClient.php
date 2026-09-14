<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

/**
 * Class LocationServiceClient
 * Cliente HTTP cURL para integração com o microserviço de geofencing (RFC-004).
 */
class LocationServiceClient
{
    const DEFAULT_SERVICE_URL = 'http://127.0.0.1:8104';
    const ENDPOINT_LOCATION_EVENTS = '/v1/location-events';
    const DEFAULT_TIMEOUT_MS = 600;
    const DEFAULT_CONNECT_TIMEOUT_MS = 300;

    /**
     * @var string
     */
    protected $serviceUrl;

    /**
     * @var int
     */
    protected $timeoutMs;

    /**
     * LocationServiceClient constructor.
     *
     * @param string|null $serviceUrl URL base do serviço de localização
     * @param int|null $timeoutMs Timeout total da requisição em milissegundos
     */
    public function __construct($serviceUrl = null, $timeoutMs = null)
    {
        $configuredUrl = Configuration::get('QLO_ARRIVAL_SERVICE_URL');
        $this->serviceUrl = $serviceUrl ?: ($configuredUrl ?: self::DEFAULT_SERVICE_URL);
        if (empty($this->serviceUrl)) {
            $this->serviceUrl = self::DEFAULT_SERVICE_URL;
        }

        $this->timeoutMs = ($timeoutMs !== null && $timeoutMs > 0) ? (int) $timeoutMs : self::DEFAULT_TIMEOUT_MS;
    }

    /**
     * Gera um identificador único universal (UUID v4) estritamente compatível com RFC 4122.
     *
     * @return string UUID v4 (ex: a1b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d)
     */
    public static function generateUuidV4()
    {
        $data = random_bytes(16);
        $data[6] = chr(ord($data[6]) & 0x0f | 0x40);
        $data[8] = chr(ord($data[8]) & 0x3f | 0x80);

        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
    }

    /**
     * Verifica se o serviço de geofencing está acessível via rota /healthz.
     *
     * @return bool Retorna true se responder HTTP 200, false caso contrário
     */
    public function isServiceAvailable()
    {
        $url = rtrim($this->serviceUrl, '/') . '/healthz';
        $ch = curl_init($url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT_MS, self::DEFAULT_CONNECT_TIMEOUT_MS);
        curl_setopt($ch, CURLOPT_TIMEOUT_MS, $this->timeoutMs);
        curl_setopt($ch, CURLOPT_NOSIGNAL, 1);

        $response = curl_exec($ch);
        $httpCode = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        return ($response !== false && $httpCode === 200);
    }

    /**
     * Envia um evento de localização para avaliação de geofence no serviço backend.
     *
     * @param array $payload Dados da requisição conforme contrato OpenAPI da RFC-004
     * @param string|null $correlationId UUID v4 para rastreabilidade (gerado se ausente)
     * @return array Resposta estruturada com status, dados e mensagem amigável de erro
     */
    public function sendLocationEvent(array $payload, $correlationId = null)
    {
        if (empty($correlationId)) {
            $correlationId = self::generateUuidV4();
        }

        $url = rtrim($this->serviceUrl, '/') . self::ENDPOINT_LOCATION_EVENTS;
        $jsonPayload = json_encode($payload);

        $ch = curl_init($url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $jsonPayload);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT_MS, self::DEFAULT_CONNECT_TIMEOUT_MS);
        curl_setopt($ch, CURLOPT_TIMEOUT_MS, $this->timeoutMs);
        curl_setopt($ch, CURLOPT_NOSIGNAL, 1);
        curl_setopt($ch, CURLOPT_HTTPHEADER, array(
            'Content-Type: application/json',
            'X-Correlation-ID: ' . $correlationId,
        ));

        $rawResponse = curl_exec($ch);
        $curlError = curl_errno($ch);
        $httpCode = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        // Tratamento de falha de conexão de rede ou timeout
        if ($curlError !== 0 || $rawResponse === false) {
            $errorMessage = 'Serviço de cálculo de proximidade temporariamente indisponível.';
            if ($curlError === CURLE_OPERATION_TIMEDOUT) {
                $errorMessage = 'Tempo limite de 600ms excedido ao comunicar com o serviço de localização.';
            }

            return array(
                'success'        => false,
                'http_code'      => $httpCode,
                'curl_error'     => $curlError,
                'correlation_id' => $correlationId,
                'data'           => null,
                'error'          => $errorMessage,
            );
        }

        $decodedResponse = json_decode($rawResponse, true);

        // Sucesso 200 OK
        if ($httpCode === 200 && is_array($decodedResponse)) {
            return array(
                'success'        => true,
                'http_code'      => 200,
                'curl_error'     => 0,
                'correlation_id' => isset($decodedResponse['correlation_id']) ? $decodedResponse['correlation_id'] : $correlationId,
                'data'           => $decodedResponse,
                'error'          => null,
            );
        }

        // Resposta de erro estruturado RFC 7807 (ex: 400 Bad Request ou 503)
        $detailError = 'Erro na resposta do serviço de localização (HTTP ' . $httpCode . ').';
        if (is_array($decodedResponse)) {
            if (!empty($decodedResponse['detail'])) {
                $detailError = $decodedResponse['detail'];
            } elseif (!empty($decodedResponse['message'])) {
                $detailError = $decodedResponse['message'];
            }
        }

        return array(
            'success'        => false,
            'http_code'      => $httpCode,
            'curl_error'     => 0,
            'correlation_id' => $correlationId,
            'data'           => $decodedResponse,
            'error'          => $detailError,
        );
    }
}
