<?php
/**
 * AdminReservationPolicyController
 *
 * @author    QloApps Engineering
 * @copyright QloApps
 * @license   AFL-3.0
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class AdminReservationPolicyController extends ModuleAdminController
{
    /**
     * Constructor
     */
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    /**
     * Initializes controller content and handles policy evaluation simulation
     */
    public function initContent()
    {
        parent::initContent();

        $evalData     = null;
        $errorMessage = null;
        $selectedPolicy = Tools::getValue('policy_type', 'MINIMUM_STAY');

        if (Tools::isSubmit('submitPolicySimulation')) {
            $policyType = Tools::getValue('policy_type');
            $corrId     = Tools::passwdGen(16, 'ALPHANUMERIC');

            $facts = array();
            if ($policyType === 'MINIMUM_STAY') {
                $facts = array(
                    'requested_nights'        => (int) Tools::getValue('requested_nights', 1),
                    'required_minimum_nights' => (int) Tools::getValue('required_minimum_nights', 2),
                    'room_type'               => Tools::getValue('room_type', 'standard'),
                );
            } elseif ($policyType === 'ADVANCE_BOOKING') {
                $facts = array(
                    'days_in_advance'  => (int) Tools::getValue('days_in_advance', 0),
                    'min_advance_days' => (int) Tools::getValue('min_advance_days', 3),
                );
            } elseif ($policyType === 'OVERBOOKING_LIMIT') {
                $facts = array(
                    'total_capacity'       => (int) Tools::getValue('total_capacity', 50),
                    'current_occupied'     => (int) Tools::getValue('current_occupied', 0),
                    'requested_units'      => (int) Tools::getValue('requested_units', 1),
                    'max_overbooking_rate' => (float) Tools::getValue('max_overbooking_rate', 0.05),
                );
            }

            $payload = json_encode(array(
                'policy' => $policyType,
                'facts'  => $facts,
            ));

            $ch = curl_init('http://127.0.0.1:8105/v1/policy-evaluations');
            curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
            curl_setopt($ch, CURLOPT_POST, true);
            curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
            curl_setopt($ch, CURLOPT_TIMEOUT_MS, 600);
            curl_setopt($ch, CURLOPT_HTTPHEADER, array(
                'Content-Type: application/json',
                'X-Correlation-ID: ' . $corrId,
            ));

            $response = curl_exec($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            $curlError = curl_error($ch);
            curl_close($ch);

            if ($response && $httpCode === 200) {
                $evalData = json_decode($response, true);
            } else {
                if ($httpCode === 400 && $response) {
                    $decodedErr = json_decode($response, true);
                    $detail = isset($decodedErr['detail']) ? $decodedErr['detail'] : '';
                    $title  = isset($decodedErr['title']) ? $decodedErr['title'] : $this->l('Fatos Inválidos');
                    if (is_array($detail)) {
                        $errorMessage = $title . ': ' . json_encode($detail);
                    } elseif ($detail) {
                        $errorMessage = $title . ': ' . $detail;
                    } else {
                        $errorMessage = $this->l('Requisição inválida (HTTP 400).');
                    }
                } else {
                    $errorMessage = sprintf(
                        $this->l('Serviço de políticas local offline ou indisponível (HTTP %d). Verifique se o servidor Python está ativo na porta 8105.'),
                        (int) $httpCode
                    );
                }
            }
        }

        $this->context->smarty->assign(array(
            'policyEvaluation' => $evalData,
            'policyError'      => $errorMessage,
            'selectedPolicy'   => $selectedPolicy,
            'currentValues'    => array(
                'requested_nights'        => (int) Tools::getValue('requested_nights', 1),
                'required_minimum_nights' => (int) Tools::getValue('required_minimum_nights', 2),
                'room_type'               => Tools::getValue('room_type', 'standard'),
                'days_in_advance'         => (int) Tools::getValue('days_in_advance', 0),
                'min_advance_days'        => (int) Tools::getValue('min_advance_days', 3),
                'total_capacity'          => (int) Tools::getValue('total_capacity', 50),
                'current_occupied'        => (int) Tools::getValue('current_occupied', 0),
                'requested_units'         => (int) Tools::getValue('requested_units', 1),
                'max_overbooking_rate'    => (float) Tools::getValue('max_overbooking_rate', 0.05),
            ),
        ));

        // Renderiza o template do simulador no painel administrativo
        $this->content = $this->createTemplate('policy_simulator.tpl')->fetch();
        $this->context->smarty->assign('content', $this->content);
    }
}
