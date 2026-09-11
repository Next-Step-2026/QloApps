<?php
/**
 * 2010-2026 QloApps
 *
 * NOTICE OF LICENSE
 *
 * This source file is subject to the Academic Free License (AFL 3.0)
 * that is bundled with this package in the file LICENSE.txt.
 * It is also available through the world-wide-web at this URL:
 * http://opensource.org/licenses/afl-3.0.php
 *
 * @author    QloApps Engineering <support@qloapps.com>
 * @copyright 2010-2026 QloApps
 * @license   http://opensource.org/licenses/afl-3.0.php Academic Free License (AFL 3.0)
 */

class AdminContactHealthController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    /**
     * Renders the contact health card view for the given customer ID.
     *
     * @return string
     */
    public function renderView()
    {
        $customerId = (int) Tools::getValue('id_customer');
        if (!$customerId) {
            $this->errors[] = $this->l('ID do cliente não informado.');
            return parent::renderView();
        }

        return $this->module->renderContactHealthCard($customerId);
    }
}
