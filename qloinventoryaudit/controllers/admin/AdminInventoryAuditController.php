<?php
// modules/qloinventoryaudit/controllers/admin/AdminInventoryAuditController.php

class AdminInventoryAuditController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    public function initContent()
    {
        parent::initContent();

        $auditResult  = null;
        $errorMessage = null;

        if (Tools::isSubmit('submitRunAudit')) {
            $rawJson = trim(Tools::getValue('audit_batch_json'));
            $corrId  = Tools::passwdGen(16, 'ALPHANUMERIC');

            $parsedData = json_decode($rawJson, true);
            if (!$parsedData || !isset($parsedData['reservations'])) {
                $errorMessage = 'JSON de reservas inválido ou malformado.';
            } else {
                $ch = curl_init('http://127.0.0.1:8107/v1/inventory-audits/overlaps');
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_POST, true);
                curl_setopt($ch, CURLOPT_POSTFIELDS, $rawJson);
                curl_setopt($ch, CURLOPT_TIMEOUT_MS, 800);
                curl_setopt($ch, CURLOPT_HTTPHEADER, [
                    'Content-Type: application/json',
                    'X-Correlation-ID: ' . $corrId
                ]);

                $response = curl_exec($ch);
                $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);

                if ($response && $httpCode === 200) {
                    $auditResult = json_decode($response, true);
                } else {
                    $errorMessage = 'Serviço de auditoria C++ offline (HTTP ' . $httpCode . ').';
                }
            }
        }

        $this->context->smarty->assign([
            'auditResult' => $auditResult,
            'auditError'  => $errorMessage
        ]);

        $this->setTemplate('audit_dashboard.tpl');
    }
}
