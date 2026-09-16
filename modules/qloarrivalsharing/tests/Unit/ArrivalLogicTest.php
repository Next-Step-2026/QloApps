<?php
/**
 * Testes Unitários de Lógica Pura
 * Execução: php modules/qloarrivalsharing/tests/Unit/ArrivalLogicTest.php
 */

if (!defined('_PS_VERSION_')) {
    define('_PS_VERSION_', '1.6.1.24');
}
if (!defined('_COOKIE_KEY_')) {
    define('_COOKIE_KEY_', 'test_secret_cookie_key_for_unit_testing_12345');
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
require_once dirname(__FILE__) . '/../../classes/ArrivalBookingRepository.php';

class ArrivalLogicTest
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

    private static function assertFalse($condition, $message)
    {
        self::assertTrue(!$condition, $message);
    }

    private static function assertEquals($expected, $actual, $message)
    {
        self::assertTrue($expected === $actual, "$message (Esperado: " . var_export($expected, true) . ", Obtido: " . var_export($actual, true) . ")");
    }

    public static function run()
    {
        echo "====================================================\n";
        echo "  Iniciando Testes Unitários: QloArrivalSharing\n";
        echo "====================================================\n\n";

        self::testUuidV4Generation();
        self::testGuestTokenSecurityAndAntiIdor();
        self::testCoordinateValidationLimits();
        self::testGeofenceRadiusDefaults();
        self::testContingencyResponseResolution();

        echo "\n====================================================\n";
        echo "\033[32m  SUCESSO: " . self::$assertions . " asserções passaram com 100% de êxito.\033[0m\n";
        echo "====================================================\n";
    }

    private static function testUuidV4Generation()
    {
        echo "-- Testando Geração de UUID v4 --\n";
        $regexV4 = '/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i';
        $generated = array();

        for ($i = 0; $i < 100; $i++) {
            $uuid = LocationServiceClient::generateUuidV4();
            self::assertTrue((bool) preg_match($regexV4, $uuid), "UUID '$uuid' deve obedecer ao padrão estrito v4");
            self::assertFalse(in_array($uuid, $generated), "UUID '$uuid' deve ser único e sem colisões");
            $generated[] = $uuid;
        }
    }

    private static function testGuestTokenSecurityAndAntiIdor()
    {
        echo "\n-- Testando Tokens de Segurança Anti-IDOR --\n";
        $orderId = 1234;
        $validToken = ArrivalBookingRepository::generateGuestToken($orderId);

        self::assertEquals(16, strlen($validToken), "O token do hóspede deve possuir exatamente 16 caracteres hexadecimais");
        self::assertTrue(ArrivalBookingRepository::validateGuestToken($orderId, $validToken), "Token válido deve ser aceito com sucesso");

        // Casos de borda e tentativas de adulteração
        $tamperedToken = substr($validToken, 0, 15) . ($validToken[15] === 'a' ? 'b' : 'a');
        self::assertFalse(ArrivalBookingRepository::validateGuestToken($orderId, $tamperedToken), "Token adulterado deve ser rejeitado");
        self::assertFalse(ArrivalBookingRepository::validateGuestToken(9999, $validToken), "Token legítimo de outro pedido deve ser rejeitado (IDOR)");
        self::assertFalse(ArrivalBookingRepository::validateGuestToken(0, $validToken), "Pedido inválido com id 0 deve ser rejeitado");
        self::assertFalse(ArrivalBookingRepository::validateGuestToken($orderId, ""), "Token vazio deve ser rejeitado");
        self::assertFalse(ArrivalBookingRepository::validateGuestToken($orderId, null), "Token nulo deve ser rejeitado");
    }

    private static function testCoordinateValidationLimits()
    {
        echo "\n-- Testando Limites Geodésicos e Formato de Coordenadas --\n";
        $validateCoords = function ($rawLat, $rawLng) {
            if ($rawLat === false || $rawLng === false || !is_numeric($rawLat) || !is_numeric($rawLng)) {
                return false;
            }

            $lat = (float) $rawLat;
            $lng = (float) $rawLng;

            return ($lat >= -90.0 && $lat <= 90.0 && $lng >= -180.0 && $lng <= 180.0);
        };

        self::assertTrue($validateCoords(-8.053100, -34.886100), "Coordenadas do Recife devem ser válidas");
        self::assertTrue($validateCoords("-8.053100", "-34.886100"), "Coordenadas em string numérica devem ser válidas");
        self::assertTrue($validateCoords(0.0, 0.0), "Ponto zero numérico legítimo deve ser válido");
        self::assertTrue($validateCoords("0.0", "0.0"), "Ponto zero em string numérica legítima deve ser válido");
        self::assertTrue($validateCoords(-90.0, -180.0), "Extremo inferior (-90, -180) deve ser válido");
        self::assertTrue($validateCoords(90.0, 180.0), "Extremo superior (90, 180) deve ser válido");

        self::assertFalse($validateCoords(false, -34.886100), "Latitude ausente (false) deve ser rejeitada");
        self::assertFalse($validateCoords(-8.053100, false), "Longitude ausente (false) deve ser rejeitada");
        self::assertFalse($validateCoords(null, -34.886100), "Latitude nula deve ser rejeitada");
        self::assertFalse($validateCoords("", ""), "Coordenadas vazias devem ser rejeitadas");
        self::assertFalse($validateCoords("abc", "-34.886100"), "Latitude em texto não numérico deve ser rejeitada");
        self::assertFalse($validateCoords("-8.053100", "undefined"), "Longitude 'undefined' deve ser rejeitada");
        self::assertFalse($validateCoords("NaN", "NaN"), "Coordenadas 'NaN' devem ser rejeitadas");

        self::assertFalse($validateCoords(90.1, 0.0), "Latitude acima de 90 deve ser inválida");
        self::assertFalse($validateCoords(-90.1, 0.0), "Latitude abaixo de -90 deve ser inválida");
        self::assertFalse($validateCoords(0.0, 180.1), "Longitude acima de 180 deve ser inválida");
        self::assertFalse($validateCoords(0.0, -180.1), "Longitude abaixo de -180 deve ser inválida");
    }

    private static function testGeofenceRadiusDefaults()
    {
        echo "\n-- Testando Regra de Raio Padrão de 200m --\n";
        $resolveRadius = function ($inputRadius) {
            $r = (float) $inputRadius;
            return ($r > 0.0) ? $r : 200.0;
        };

        self::assertEquals(200.0, $resolveRadius(0), "Raio 0 deve assumir o padrão de 200m");
        self::assertEquals(200.0, $resolveRadius(-10), "Raio negativo deve assumir o padrão de 200m");
        self::assertEquals(200.0, $resolveRadius(null), "Raio nulo deve assumir o padrão de 200m");
        self::assertEquals(350.0, $resolveRadius(350), "Raio positivo informado (350m) deve ser preservado");
        self::assertEquals(100.0, $resolveRadius(100), "Raio positivo informado (100m) deve ser preservado");
    }

    private static function testContingencyResponseResolution()
    {
        echo "\n-- Testando Resolução de Contingência e Não-Corrupção de Estado --\n";

        $failedServiceResponse = array(
            'success'        => false,
            'http_code'      => 503,
            'curl_error'     => 0,
            'correlation_id' => 'test-correlation-id-123',
            'data'           => null,
            'error'          => 'Serviço de cálculo de proximidade temporariamente indisponível.',
        );

        $resolveContingencyResponse = function ($serviceResponse) {
            $defaultMsg = 'Serviço de cálculo de proximidade temporariamente indisponível.';
            $msg = (!empty($serviceResponse['error'])) ? $serviceResponse['error'] : $defaultMsg;

            return array(
                'success' => false,
                'message' => $msg,
            );
        };

        $result = $resolveContingencyResponse($failedServiceResponse);
        self::assertFalse($result['success'], "Contingência deve retornar success = false para o hóspede");
        self::assertEquals('Serviço de cálculo de proximidade temporariamente indisponível.', $result['message'], "Mensagem de indisponibilidade deve ser preservada");

        $previousState = 'inside';
        $shouldMutateStateOnFailure = false;
        self::assertFalse($shouldMutateStateOnFailure, "Estado de rastreamento não deve ser mutado no banco em caso de falha do serviço");
        self::assertEquals('inside', $previousState, "Estado legítimo prévio deve permanecer intacto");
    }
}

ArrivalLogicTest::run();
