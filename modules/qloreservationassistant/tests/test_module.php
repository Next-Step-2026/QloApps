<?php
/**
 * Testes Automatizados de Unidade e Integracao do Modulo qloreservationassistant
 * Execucao: php modules/qloreservationassistant/tests/test_module.php
 */

define('_PS_ADMIN_DIR_', dirname(__DIR__, 3) . '/admin338bwc0sf');
require_once dirname(__DIR__, 3) . '/config/config.inc.php';

Context::getContext()->employee = new Employee(1);
Context::getContext()->language = new Language(1);

$passed = 0;
$failed = 0;

function assert_test($description, $condition) {
    global $passed, $failed;
    if ($condition) {
        echo "  [PASS] $description\n";
        $passed++;
    } else {
        echo "  [FAIL] $description\n";
        $failed++;
    }
}

echo "=== INICIANDO SUITE DE TESTES DO MODULO QLORESERVATIONASSISTANT ===\n\n";

// 1. Validacao da Classe do Modulo
echo "[GRUPO 1] Verificacao da Estrutura do Modulo\n";
require_once dirname(__DIR__) . '/qloreservationassistant.php';
$module = new QloReservationAssistant();
assert_test("Nome do modulo deve ser qloreservationassistant", $module->name === 'qloreservationassistant');
assert_test("Versao deve ser 1.0.0", $module->version === '1.0.0');
assert_test("Bootstrap deve estar ativo", $module->bootstrap === true);
assert_test("Aba deve ser hotel_reservation", $module->tab === 'hotel_reservation');
assert_test("Modulo registrado e ativo no banco", $module->isInstalled('qloreservationassistant'));

// 2. Validacao do Controller
echo "\n[GRUPO 2] Verificacao do Controller Administrativo\n";
require_once dirname(__DIR__) . '/controllers/admin/AdminReservationAssistantController.php';
$ctrl = new AdminReservationAssistantController();
assert_test("Controller deve ter timeout de 600ms", AdminReservationAssistantController::TIMEOUT_MS === 600);
assert_test("Controller deve apontar para porta 8101", AdminReservationAssistantController::SERVICE_URL === 'http://127.0.0.1:8101/v1/assist/interpret');
assert_test("Bootstrap do controller deve ser true", $ctrl->bootstrap === true);

// 3. Validacao do Cenário BDD 4: Servico C++ Offline (Contingencia)
echo "\n[GRUPO 3] BDD Cenario 4: Contingencia com Servico Offline\n";
$_POST = array();
$_POST['submitQueryAssistant'] = 1;
$_POST['user_query'] = 'tem quarto deluxe para depois de amanha?';
$_POST['reference_date'] = '2026-08-27';

$ctrlOffline = new AdminReservationAssistantController();
$start = microtime(true);
$ctrlOffline->initContent();
$duration = (microtime(true) - $start) * 1000;

assert_test("Tempo de resposta do fallback <= 650ms", $duration <= 650);
assert_test("Aviso de contingencia (503) exibido no template", strpos($ctrlOffline->content, 'Serviço de inferência local indisponível') !== false);
assert_test("Template preservou painel sem excecoes nao tratadas", strpos($ctrlOffline->content, 'Copiloto de Atendimento e Consulta de Reservas') !== false);

// 4. Validacao de Casos de Borda de Entrada
echo "\n[GRUPO 4] Validacao de Casos de Borda de Entrada\n";
$_POST = array();
$_POST['submitQueryAssistant'] = 1;
$_POST['user_query'] = '';
$_POST['reference_date'] = '2026-08-27';

$ctrlEmpty = new AdminReservationAssistantController();
$ctrlEmpty->initContent();
assert_test("Query vazia deve exibir erro de digitacao", strpos($ctrlEmpty->content, 'Por favor, digite a pergunta do hóspede') !== false);

$_POST['user_query'] = str_repeat('a', 300);
$ctrlLong = new AdminReservationAssistantController();
$ctrlLong->initContent();
assert_test("Query > 256 caracteres deve ser rejeitada", strpos($ctrlLong->content, 'excede o limite máximo de 256 caracteres') !== false);

echo "\n=== RESUMO DOS TESTES DO MODULO ===\n";
echo "Total de Testes: " . ($passed + $failed) . " | Passaram: $passed | Falharam: $failed\n";

if ($failed > 0) {
    exit(1);
}
exit(0);
