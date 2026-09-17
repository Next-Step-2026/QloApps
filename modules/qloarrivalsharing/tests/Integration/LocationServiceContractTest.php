<?php
/**
 * Testes de Integração e Contrato cURL com o Serviço de Localização
 * Execução: php modules/qloarrivalsharing/tests/Integration/LocationServiceContractTest.php
 */

if (!defined('_PS_VERSION_')) {
    define('_PS_VERSION_', '1.6.1.24');
}

if (!class_exists('Configuration')) {
    class Configuration
    {
        public static function get($key)
        {
            return null;
        }
    }
}

require_once dirname(__FILE__) . '/../../classes/LocationServiceClient.php';

class LocationServiceContractTest
{
    private static $assertions = 0;

    private static function assertTrue($condition, $message)
    {
        self::$assertions++;
        if (!$condition) {
            echo "\033[31m[FALHA]\033[0m $message\n";
            exit(1);
        }
        echo "\033[32m[OK]\033[0m $message\n";
    }

    private static function assertEquals($expected, $actual, $message)
    {
        self::assertTrue($expected === $actual, "$message (Esperado: " . var_export($expected, true) . ", Obtido: " . var_export($actual, true) . ")");
    }

    public static function run()
    {
        echo "====================================================\n";
        echo "  Iniciando Testes de Integração cURL / Contrato\n";
        echo "====================================================\n\n";

        self::testOfflineResilienceAndTimeout();
        self::testLiveServiceContractIfRunning();

        echo "\n====================================================\n";
        echo "\033[32m  SUCESSO: " . self::$assertions . " asserções de contrato passaram.\033[0m\n";
        echo "====================================================\n";
    }

    private static function testOfflineResilienceAndTimeout()
    {
        echo "-- Testando Resiliência de Timeout e Serviço Offline (Contingência) --\n";
        // Aponta para uma porta fechada em loopback com timeout de 600ms
        $offlineClient = new LocationServiceClient('http://127.0.0.1:8199', 600);

        $startTime = microtime(true);
        $isAvailable = $offlineClient->isServiceAvailable();
        $duration = (microtime(true) - $startTime) * 1000;

        self::assertTrue(!$isAvailable, "Serviço em porta offline deve retornar false no healthcheck");
        self::assertTrue($duration < 1200, "Checagem de serviço offline deve retornar rapidamente (levou " . round($duration, 1) . "ms)");

        $payload = array(
            'hotel_id'          => 'htl-recife-01',
            'hotel_lat'         => -8.052240,
            'hotel_lng'         => -34.885650,
            'guest_lat'         => -8.053100,
            'guest_lng'         => -34.886100,
            'geofence_radius_m' => 200.0,
            'previous_state'    => 'outside',
        );

        $startTime = microtime(true);
        $response = $offlineClient->sendLocationEvent($payload);
        $duration = (microtime(true) - $startTime) * 1000;

        self::assertTrue(!$response['success'], "Envio para porta offline deve retornar success = false");
        self::assertTrue(!empty($response['error']), "Resposta deve conter mensagem amigável de erro");
        self::assertTrue($duration < 1200, "Envio para serviço offline deve respeitar timeout de contingência (levou " . round($duration, 1) . "ms)");
    }

    private static function testLiveServiceContractIfRunning()
    {
        echo "\n-- Verificando Serviço Kotlin Ativo (127.0.0.1:8104) --\n";
        $liveClient = new LocationServiceClient('http://127.0.0.1:8104', 600);

        if (!$liveClient->isServiceAvailable()) {
            echo "\033[33m[AVISO]\033[0m Serviço Kotlin na porta 8104 está offline no momento. Pule testes online de loopback.\n";
            return;
        }

        echo "\033[32m[INFO]\033[0m Serviço Kotlin detectado online! Executando validação de contrato de API:\n";

        // 1. Cenário de Entrada no Raio (ENTERED)
        $payloadEntered = array(
            'hotel_id'          => 'htl-recife-01',
            'hotel_lat'         => -8.052240,
            'hotel_lng'         => -34.885650,
            'guest_lat'         => -8.053100,
            'guest_lng'         => -34.886100,
            'geofence_radius_m' => 200.0,
            'previous_state'    => 'outside',
        );

        $resEntered = $liveClient->sendLocationEvent($payloadEntered);
        self::assertTrue($resEntered['success'], "Requisição para hóspede próximo deve ser bem-sucedida");
        self::assertEquals(200, $resEntered['http_code'], "Status HTTP deve ser 200");
        self::assertEquals('inside', $resEntered['data']['current_state'], "Status deve ser 'inside'");
        self::assertEquals('ENTERED', $resEntered['data']['transition'], "Transição deve ser 'ENTERED'");
        self::assertTrue($resEntered['data']['alert_triggered'], "Alerta deve ser acionado");

        // 2. Cenário Distante (NO_CHANGE)
        $payloadDistant = array(
            'hotel_id'          => 'htl-recife-01',
            'hotel_lat'         => -8.052240,
            'hotel_lng'         => -34.885650,
            'guest_lat'         => -8.065000,
            'guest_lng'         => -34.890000,
            'geofence_radius_m' => 200.0,
            'previous_state'    => 'outside',
        );

        $resDistant = $liveClient->sendLocationEvent($payloadDistant);
        self::assertTrue($resDistant['success'], "Requisição para hóspede distante deve ser bem-sucedida");
        self::assertEquals('outside', $resDistant['data']['current_state'], "Status deve ser 'outside'");
        self::assertEquals('NO_CHANGE', $resDistant['data']['transition'], "Transição deve ser 'NO_CHANGE'");
        self::assertTrue(!$resDistant['data']['alert_triggered'], "Alerta não deve ser acionado");

        $payloadInvalid = array(
            'hotel_id'          => 'htl-recife-01',
            'hotel_lat'         => -8.052240,
            'hotel_lng'         => -34.885650,
            'guest_lat'         => 999.0, // Latitude inválida
            'guest_lng'         => -34.886100,
            'geofence_radius_m' => 200.0,
            'previous_state'    => 'outside',
        );

        $resInvalid = $liveClient->sendLocationEvent($payloadInvalid);
        self::assertTrue(!$resInvalid['success'], "Coordenada inválida deve retornar erro");
        self::assertEquals(400, $resInvalid['http_code'], "Status HTTP deve ser 400");
        self::assertTrue(!empty($resInvalid['error']), "Deve conter mensagem de erro RFC 7807 detalhada");
    }
}

LocationServiceContractTest::run();
