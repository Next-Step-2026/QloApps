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
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloReservationAssistant extends Module
{
    public function __construct()
    {
        $this->name = 'qloreservationassistant';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Copiloto de Consulta de Reservas');
        $this->description = $this->l('Assistente determinístico para interpretação de dúvidas de disponibilidade e políticas.');
        $this->confirmUninstall = $this->l('Tem certeza de que deseja desinstalar o Copiloto de Consulta de Reservas?');
    }

    public function install()
    {
        return parent::install() && $this->installTab();
    }

    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    private function installTab()
    {
        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminReservationAssistant';
        $tab->name = array();

        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Copiloto de Reservas';
        }

        $idParent = (int) Tab::getIdFromClassName('AdminParentOrders');
        if (!$idParent) {
            $idParent = (int) Tab::getIdFromClassName('AdminOrders');
        }

        $tab->id_parent = $idParent;
        $tab->module = $this->name;

        return (bool) $tab->add();
    }

    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminReservationAssistant');
        if ($idTab) {
            $tab = new Tab($idTab);
            return (bool) $tab->delete();
        }

        return true;
    }
}
