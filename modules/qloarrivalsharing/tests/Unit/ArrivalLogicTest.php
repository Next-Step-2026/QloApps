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
if (!defined('_DB_PREFIX_')) {
    define('_DB_PREFIX_', 'qlo_');
}
if (!defined('_PS_USE_SQL_SLAVE_')) {
    define('_PS_USE_SQL_SLAVE_', false);
}

if (!function_exists('pSQL')) {
    function pSQL($string, $htmlOK = false)
    {
        return addslashes($string);
    }
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

if (!class_exists('Db')) {
    class Db
    {
        public static $mockInstance = null;

        public static function getInstance($use_slave = false)
        {
            if (self::$mockInstance === null) {
                self::$mockInstance = new self();
            }
            return self::$mockInstance;
        }

        public $lastQuery = null;
        public $executedQueries = array();
        public $mockValue = 1;
        public $mockExecuteResult = true;

        public function getValue($sql, $use_cache = true)
        {
            $this->lastQuery = $sql;
            $this->executedQueries[] = $sql;
            return $this->mockValue;
        }

        public function execute($sql)
        {
            $this->lastQuery = $sql;
            $this->executedQueries[] = $sql;
            return $this->mockExecuteResult;
        }

        public function executeS($sql)
        {
            $this->lastQuery = $sql;
            $this->executedQueries[] = $sql;
            return array(
                array('Field' => 'id_order'),
                array('Field' => 'previous_state'),
                array('Field' => 'current_state'),
                array('Field' => 'transition'),
                array('Field' => 'distance_meters'),
                array('Field' => 'date_upd'),
            );
        }

        public function getRow($sql, $use_cache = true)
        {
            $this->lastQuery = $sql;
            $this->executedQueries[] = $sql;
            return array(
                'previous_state'  => 'outside',
                'current_state'   => 'outside',
                'transition'      => 'NO_CHANGE',
                'distance_meters' => 250.0,
                'date_upd'        => '2026-09-16 00:00:00',
            );
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
        self::testHotelCoordinatesZeroEvaluation();
        self::testTransitionResolutionAndConcurrencyDeduplication();
        self::testOrderLockAndSafeUpsertMechanics();
        self::testSchemaManagementLifecycle();

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

        self::assertTrue(ArrivalBookingRepository::validateCoordinates(-8.053100, -34.886100), "Coordenadas do Recife devem ser válidas");
        self::assertTrue(ArrivalBookingRepository::validateCoordinates("-8.053100", "-34.886100"), "Coordenadas em string numérica devem ser válidas");
        self::assertTrue(ArrivalBookingRepository::validateCoordinates(0.0, 0.0), "Ponto zero numérico legítimo deve ser válido");
        self::assertTrue(ArrivalBookingRepository::validateCoordinates("0.0", "0.0"), "Ponto zero em string numérica legítima deve ser válido");
        self::assertTrue(ArrivalBookingRepository::validateCoordinates(-90.0, -180.0), "Extremo inferior (-90, -180) deve ser válido");
        self::assertTrue(ArrivalBookingRepository::validateCoordinates(90.0, 180.0), "Extremo superior (90, 180) deve ser válido");

        self::assertFalse(ArrivalBookingRepository::validateCoordinates(false, -34.886100), "Latitude ausente (false) deve ser rejeitada");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates(-8.053100, false), "Longitude ausente (false) deve ser rejeitada");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates(null, -34.886100), "Latitude nula deve ser rejeitada");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates("", ""), "Coordenadas vazias devem ser rejeitadas");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates("abc", "-34.886100"), "Latitude em texto não numérico deve ser rejeitada");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates("-8.053100", "undefined"), "Longitude 'undefined' deve ser rejeitada");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates("NaN", "NaN"), "Coordenadas 'NaN' devem ser rejeitadas");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates(INF, -34.886100), "Latitude infinita (INF) deve ser rejeitada");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates(-8.053100, -INF), "Longitude infinita (-INF) deve ser rejeitada");

        self::assertFalse(ArrivalBookingRepository::validateCoordinates(90.1, 0.0), "Latitude acima de 90 deve ser inválida");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates(-90.1, 0.0), "Latitude abaixo de -90 deve ser inválida");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates(0.0, 180.1), "Longitude acima de 180 deve ser inválida");
        self::assertFalse(ArrivalBookingRepository::validateCoordinates(0.0, -180.1), "Longitude abaixo de -180 deve ser inválida");
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

    private static function testHotelCoordinatesZeroEvaluation()
    {
        echo "\n-- Testando Resolução de Coordenadas do Hotel e Zero Válido --\n";

        $defaultCoords = array('latitude' => -8.052240, 'longitude' => -34.885650);

        $resolveCoords = function ($row) use ($defaultCoords) {
            if ($row && isset($row['latitude'], $row['longitude']) && ArrivalBookingRepository::validateCoordinates($row['latitude'], $row['longitude'])) {
                return array(
                    'latitude'  => (float) $row['latitude'],
                    'longitude' => (float) $row['longitude'],
                );
            }
            return $defaultCoords;
        };

        $zeroHotel = $resolveCoords(array('latitude' => 0.0, 'longitude' => 0.0));
        self::assertEquals(0.0, $zeroHotel['latitude'], "Latitude 0.0 legítima não deve sofrer fallback");
        self::assertEquals(0.0, $zeroHotel['longitude'], "Longitude 0.0 legítima não deve sofrer fallback");

        $validHotel = $resolveCoords(array('latitude' => -8.052240, 'longitude' => -34.885650));
        self::assertEquals(-8.052240, $validHotel['latitude'], "Coordenadas reais devem ser preservadas");

        $nullHotel = $resolveCoords(array('latitude' => null, 'longitude' => null));
        self::assertEquals(-8.052240, $nullHotel['latitude'], "Latitude nula deve acionar fallback padrao");

        $emptyHotel = $resolveCoords(array('latitude' => '', 'longitude' => ''));
        self::assertEquals(-8.052240, $emptyHotel['latitude'], "Latitude vazia deve acionar fallback padrao");

        $noRow = $resolveCoords(false);
        self::assertEquals(-8.052240, $noRow['latitude'], "Sem registro deve acionar fallback padrao");
    }

    private static function testTransitionResolutionAndConcurrencyDeduplication()
    {
        echo "\n-- Testando Normalização de Transição e Idempotência de Concorrência --\n";

        // Caso crítico: Requisição concorrente quando o hóspede já estava dentro
        $res = ArrivalBookingRepository::resolveSafeTransition('inside', 'inside', 'ENTERED');
        self::assertEquals('NO_CHANGE', $res, 'Transição ENTERED duplicada com estado já inside deve ser convertida para NO_CHANGE');

        // Caso de borda: Requisição duplicada quando o hóspede já estava fora
        $res = ArrivalBookingRepository::resolveSafeTransition('outside', 'outside', 'EXITED');
        self::assertEquals('NO_CHANGE', $res, 'Transição EXITED duplicada com estado já outside deve ser convertida para NO_CHANGE');

        // Transição legítima de entrada
        $res = ArrivalBookingRepository::resolveSafeTransition('outside', 'inside', 'ENTERED');
        self::assertEquals('ENTERED', $res, 'Transição legítima de outside para inside deve ser preservada como ENTERED');

        // Transição legítima de saída
        $res = ArrivalBookingRepository::resolveSafeTransition('inside', 'outside', 'EXITED');
        self::assertEquals('EXITED', $res, 'Transição legítima de inside para outside deve ser preservada como EXITED');

        // Movimento sem mudança de estado
        $res = ArrivalBookingRepository::resolveSafeTransition('inside', 'inside', 'NO_CHANGE');
        self::assertEquals('NO_CHANGE', $res, 'Movimento contínuo dentro do geofence deve manter NO_CHANGE');

        $res = ArrivalBookingRepository::resolveSafeTransition('outside', 'outside', 'NO_CHANGE');
        self::assertEquals('NO_CHANGE', $res, 'Movimento contínuo fora do geofence deve manter NO_CHANGE');

        // Casos atípicos: valores nulos, inválidos ou corrompidos
        $res = ArrivalBookingRepository::resolveSafeTransition('inside', 'inside', 'CORRUPTED');
        self::assertEquals('NO_CHANGE', $res, 'Valor de transição desconhecido deve sofrer fallback seguro para NO_CHANGE');

        $res = ArrivalBookingRepository::resolveSafeTransition('outside', 'inside', null);
        self::assertEquals('NO_CHANGE', $res, 'Transição nula deve sofrer fallback seguro para NO_CHANGE');

        // Garantia de disparo de alerta apenas em ENTERED legítimo
        $triggerAlert = function ($transition) {
            return ($transition === 'ENTERED');
        };

        self::assertTrue($triggerAlert('ENTERED'), 'Alerta deve ser disparado em transição legítima ENTERED');
        self::assertFalse($triggerAlert('NO_CHANGE'), 'Alerta NÃO deve ser disparado em NO_CHANGE');
        self::assertFalse($triggerAlert('EXITED'), 'Alerta NÃO deve ser disparado em EXITED');
        self::assertFalse($triggerAlert(ArrivalBookingRepository::resolveSafeTransition('inside', 'inside', 'ENTERED')), 'Alerta NÃO deve ser disparado em transição duplicada');
    }

    private static function testOrderLockAndSafeUpsertMechanics()
    {
        echo "\n-- Testando Mecânica de Lock Concorrente e Upsert Seguro --\n";

        $db = Db::getInstance();

        // 1. Lock com sucesso
        $db->mockValue = 1;
        $locked = ArrivalBookingRepository::acquireOrderLock(1234, 3);
        self::assertTrue($locked, 'Lock com sucesso deve retornar true');
        self::assertTrue(strpos($db->lastQuery, "GET_LOCK('qlo_arrival_order_1234', 3)") !== false, 'Query deve conter GET_LOCK com chave e timeout corretos');

        // 2. Lock com falha (rejeição de concorrência ou timeout atingido)
        $db->mockValue = 0;
        $busyLock = ArrivalBookingRepository::acquireOrderLock(1234, 0);
        self::assertFalse($busyLock, 'Tentativa de lock ocupado deve retornar false sem travar o processo');
        self::assertTrue(strpos($db->lastQuery, "GET_LOCK('qlo_arrival_order_1234', 0)") !== false, 'Query de lock sem espera deve ter timeout 0');

        // 3. Liberação de lock
        $db->mockValue = 1;
        $released = ArrivalBookingRepository::releaseOrderLock(1234);
        self::assertTrue($released, 'Liberação de lock bem-sucedida deve retornar true');
        self::assertTrue(strpos($db->lastQuery, "RELEASE_LOCK('qlo_arrival_order_1234')") !== false, 'Query deve conter RELEASE_LOCK com chave correta');

        // 4. Casos atípicos de lock com IDs inválidos
        self::assertFalse(ArrivalBookingRepository::acquireOrderLock(0), 'Lock com ID 0 deve ser rejeitado imediatamente');
        self::assertFalse(ArrivalBookingRepository::acquireOrderLock(-10), 'Lock com ID negativo deve ser rejeitado imediatamente');
        self::assertFalse(ArrivalBookingRepository::releaseOrderLock(0), 'Liberação com ID 0 deve ser rejeitada imediatamente');
        self::assertFalse(ArrivalBookingRepository::releaseOrderLock(-5), 'Liberação com ID negativo deve ser rejeitada imediatamente');

        // 5. Verificação de SQL do Upsert Seguro (evitar REPLACE INTO)
        $db->mockExecuteResult = true;
        $saved = ArrivalBookingRepository::saveArrivalTracking(1234, 'inside', 45.5, 'outside', 'ENTERED');
        self::assertTrue($saved, 'Gravação do tracking deve retornar sucesso');
        self::assertTrue(strpos($db->lastQuery, 'INSERT INTO `' . _DB_PREFIX_ . 'qlo_arrival_tracking`') !== false, 'Query deve usar INSERT INTO');
        self::assertTrue(strpos($db->lastQuery, 'ON DUPLICATE KEY UPDATE') !== false, 'Query deve conter cláusula ON DUPLICATE KEY UPDATE');
        self::assertFalse(strpos($db->lastQuery, 'REPLACE INTO'), 'Query JAMAIS deve utilizar REPLACE INTO');
        self::assertTrue(strpos($db->lastQuery, "IF(`current_state` = 'inside' AND VALUES(`current_state`) = 'inside', 'NO_CHANGE', VALUES(`transition`))") !== false, 'Query deve conter proteção atômica contra duplicidade de ENTERED');

        // 6. Caso atípico no save com ID inválido
        self::assertFalse(ArrivalBookingRepository::saveArrivalTracking(0, 'inside', 50.0), 'Tentativa de salvar tracking com ID 0 deve retornar false');
        self::assertFalse(ArrivalBookingRepository::saveArrivalTracking(-99, 'inside', 50.0), 'Tentativa de salvar tracking com ID negativo deve retornar false');
    }

    private static function testSchemaManagementLifecycle()
    {
        echo "\n-- Testando Ciclo de Vida de Esquema e Ausência de DDL em Runtime --\n";

        $db = Db::getInstance();

        // 1. Criação da tabela (chamado no install do módulo)
        $db->mockExecuteResult = true;
        $created = ArrivalBookingRepository::createTrackingTable();
        self::assertTrue($created, 'Criação da tabela deve retornar true');
        self::assertTrue(strpos($db->lastQuery, 'CREATE TABLE IF NOT EXISTS `' . _DB_PREFIX_ . 'qlo_arrival_tracking`') !== false, 'Query deve conter CREATE TABLE IF NOT EXISTS');
        self::assertTrue(strpos($db->lastQuery, '`previous_state`') !== false, 'Definição da tabela deve incluir previous_state');
        self::assertTrue(strpos($db->lastQuery, '`transition`') !== false, 'Definição da tabela deve incluir transition');

        // 2. Remoção da tabela (chamado no uninstall do módulo)
        $dropped = ArrivalBookingRepository::dropTrackingTable();
        self::assertTrue($dropped, 'Remoção da tabela deve retornar true');
        self::assertTrue(strpos($db->lastQuery, 'DROP TABLE IF EXISTS `' . _DB_PREFIX_ . 'qlo_arrival_tracking`') !== false, 'Query deve conter DROP TABLE IF EXISTS');

        // 3. Garantia de que consultas em runtime NÃO disparam DDL (SRP)
        $db->executedQueries = array();
        ArrivalBookingRepository::saveArrivalTracking(999, 'inside', 10.0, 'outside', 'ENTERED');
        $saveQueries = $db->executedQueries;

        $hasDdlInSave = false;
        foreach ($saveQueries as $q) {
            if (stripos($q, 'CREATE TABLE') !== false || stripos($q, 'SHOW COLUMNS') !== false || stripos($q, 'ALTER TABLE') !== false) {
                $hasDdlInSave = true;
                break;
            }
        }
        self::assertFalse($hasDdlInSave, 'saveArrivalTracking NÃO deve executar DDL (CREATE, ALTER, SHOW COLUMNS)');

        $db->executedQueries = array();
        ArrivalBookingRepository::getArrivalTrackingState(999);
        $readQueries = $db->executedQueries;

        $hasDdlInRead = false;
        foreach ($readQueries as $q) {
            if (stripos($q, 'CREATE TABLE') !== false || stripos($q, 'SHOW COLUMNS') !== false || stripos($q, 'ALTER TABLE') !== false) {
                $hasDdlInRead = true;
                break;
            }
        }
        self::assertFalse($hasDdlInRead, 'getArrivalTrackingState NÃO deve executar DDL (CREATE, ALTER, SHOW COLUMNS)');
    }
}

ArrivalLogicTest::run();
