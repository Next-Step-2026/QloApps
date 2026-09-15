<?php
/**
 * @file qloreservationpolicy.php
 * @brief Módulo QloReservationPolicy para QloApps.
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

/**
 * @class QloReservationPolicy
 * @brief Gerencia o ciclo de vida e abas administrativas do Motor de Políticas.
 * @details Validador determinístico de regras de estadia mínima, antecedência e overbooking.
 */
class QloReservationPolicy extends Module
{
    /**
     * @brief Construtor do módulo.
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
     * @brief Instala o módulo e cria a aba no menu do Back-Office.
     * @return bool True em caso de sucesso, False caso contrário.
     */
    public function install()
    {
        return parent::install() && $this->installTab();
    }

    /**
     * @brief Desinstala o módulo e remove a aba do Back-Office.
     * @return bool True em caso de sucesso, False caso contrário.
     */
    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    /**
     * @brief Cria a aba de navegação (AdminTab) no menu de Gerenciamento de Reservas.
     * @return bool True se criada com sucesso.
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
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminHotelReservationSystemManagement');
        $tab->module = $this->name;

        return (bool) $tab->add();
    }

    /**
     * @brief Remove a aba de menu do Back-Office durante a desinstalação.
     * @return bool True se removida ou se já inexistente.
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
