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

class AdminExternalRequestsController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->toolbar_title = $this->l('Conversor de Canais Externos');
    }

    /**
     * @return void
     */
    public function initContent()
    {
        parent::initContent();

        $this->setTemplate('request_converter.tpl');
    }
}
