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

class QloExternalRequests extends Module
{
    public function __construct()
    {
        $this->name = 'qloexternalrequests';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Conversor de Canais Externos');
        $this->description = $this->l('Normalização e conversão de solicitações de reserva para modelo canônico.');
    }

    /**
     * @return bool
     */
    public function install()
    {
        return parent::install() && $this->installTab();
    }

    /**
     * @return bool
     */
    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    /**
     * @return bool
     */
    private function installTab()
    {
        $tab = new Tab();
        $tab->active = true;
        $tab->class_name = 'AdminExternalRequests';
        $tab->name = [];
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = $this->l('Conversor de Canais');
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;
        return (bool) $tab->add();
    }

    /**
     * @return bool
     */
    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminExternalRequests');
        if ($idTab) {
            $tab = new Tab($idTab);
            return (bool) $tab->delete();
        }
        return true;
    }
}
