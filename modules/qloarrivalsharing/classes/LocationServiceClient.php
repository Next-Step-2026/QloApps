<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

/**
 * Cliente HTTP para integração com o serviço de geofencing.
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
     * Inicializa a URL do serviço e o timeout padrão.
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
     * Gera UUID v4.
     *
     * @return string
     */
    public static function generateUuidV4()
    {
        $data = random_bytes(16);
        $data[6] = chr(ord($data[6]) & 0x0f | 0x40);
        $data[8] = chr(ord($data[8]) & 0x3f | 0x80);

        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
    }

    /**
     * Verifica se o serviço de geofencing está acessível via /healthz.
     *
     * @return bool
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
     * Envia evento de localização para avaliação de geofence.
     *
     * @return array
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
