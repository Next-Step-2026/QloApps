<?php
/**
 * QloReservationPolicy Module for QloApps
 *
 * @author    QloApps Engineering
 * @copyright QloApps
 * @license   AFL-3.0
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloReservationPolicy extends Module
{
    /**
     * Constructor for QloReservationPolicy
     */
    public function __construct()
    {
        $this->name = 'qloreservationpolicy';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Motor de Políticas de Reserva');
        $this->description = $this->l('Validador determinístico de regras de estadia mínima, antecedência e overbooking.');
    }

    /**
     * Module installation
     *
     * @return bool
     */
    public function install()
    {
        return parent::install() && $this->installTab();
    }

    /**
     * Module uninstallation
     *
     * @return bool
     */
    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    /**
     * Installs back-office AdminTab for the module
     *
     * @return bool
     */
    private function installTab()
    {
        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminReservationPolicy';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = $this->l('Políticas de Reserva');
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;

        return (bool) $tab->add();
    }

    /**
     * Uninstalls back-office AdminTab for the module
     *
     * @return bool
     */
    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminReservationPolicy');
        if ($idTab) {
            $tab = new Tab($idTab);
            return (bool) $tab->delete();
        }
        return true;
    }
}
