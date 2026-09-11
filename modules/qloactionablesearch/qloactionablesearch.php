<?php
/**
 * QloApps Module: qloactionablesearch
 *
 * Motor Lexical de Busca Rapida e Reconhecimento de Entidades de Catalogo (QLO-FEAT-008)
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloActionableSearch extends Module
{
    public function __construct()
    {
        $this->name = 'qloactionablesearch';
        $this->tab = 'front_office_features';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Busca de Entidades Acionáveis');
        $this->description = $this->l('Motor lexical rápido para pesquisa de quartos e comodidades de catálogo.');
        $this->ps_versions_compliancy = array('min' => '1.6', 'max' => _PS_VERSION_);
    }

    public function install()
    {
        return parent::install() 
            && $this->installTab()
            && Configuration::updateValue('QLO_ACTIONABLE_SEARCH_URL', 'http://host.docker.internal:8108/v1/search/parse');
    }

    public function uninstall()
    {
        return $this->uninstallTab() 
            && Configuration::deleteByName('QLO_ACTIONABLE_SEARCH_URL')
            && parent::uninstall();
    }

    /**
     * Registra a aba no menu administrativo do Back Office
     */
    private function installTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminActionableSearch');
        if ($idTab) {
            return true;
        }

        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminActionableSearch';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Busca de Entidades';
        }

        // Tenta associar sob o menu de Pedidos/Bookings, senao deixa na raiz
        $idParent = (int) Tab::getIdFromClassName('AdminParentOrders');
        if (!$idParent) {
            $idParent = (int) Tab::getIdFromClassName('AdminOrders');
        }
        $tab->id_parent = $idParent;
        $tab->module = $this->name;

        $res = (bool) $tab->add();
        if ($res && $tab->id) {
            $profiles = Profile::getProfiles(Context::getContext()->language->id);
            if ($profiles) {
                foreach ($profiles as $prof) {
                    Db::getInstance()->execute('INSERT INTO '._DB_PREFIX_.'access (id_profile, id_tab, `view`, `add`, `edit`, `delete`) VALUES ('
                        .(int)$prof['id_profile'].', '.(int)$tab->id.', 1, 1, 1, 1) ON DUPLICATE KEY UPDATE `view`=1, `add`=1, `edit`=1, `delete`=1');
                }
            }
        }

        return $res;
    }

    /**
     * Remove a aba do menu administrativo
     */
    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminActionableSearch');
        if ($idTab) {
            $tab = new Tab($idTab);
            return (bool) $tab->delete();
        }
        return true;
    }
}
