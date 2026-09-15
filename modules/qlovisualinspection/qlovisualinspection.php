<?php
/**
 * Visual Inspection & Image Quality Assessment Module for Housekeeping Governance
 *
 * @author    QloApps Engineering
 * @copyright Since 2026 QloApps
 * @license   https://opensource.org/licenses/AFL-3.0 Academic Free License 3.0 (AFL-3.0)
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloVisualInspection extends Module
{
    public function __construct()
    {
        $this->name = 'qlovisualinspection';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Room Visual Inspection');
        $this->description = $this->l('Objective luminance and sharpness quality assessment metrics for housekeeping governance.');
        $this->ps_versions_compliancy = array('min' => '1.6', 'max' => _PS_VERSION_);
    }

    /**
     * Module installation
     *
     * @return bool
     */
    public function install()
    {
        return parent::install() && $this->installTab() && $this->createTable();
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
     * Create inspection history table
     *
     * @return bool
     */
    private function createTable()
    {
        $sql = 'CREATE TABLE IF NOT EXISTS `' . _DB_PREFIX_ . 'visual_inspection` (
            `id_visual_inspection` int(11) NOT NULL AUTO_INCREMENT,
            `inspection_id` varchar(64) NOT NULL,
            `id_room` int(11) NOT NULL,
            `room_num` varchar(64) NOT NULL,
            `id_employee` int(11) NOT NULL DEFAULT 0,
            `employee_name` varchar(128) NOT NULL DEFAULT \'\',
            `item_key` varchar(32) NOT NULL,
            `item_title` varchar(64) NOT NULL,
            `image_path` varchar(255) NOT NULL,
            `width` int(11) NOT NULL DEFAULT 0,
            `height` int(11) NOT NULL DEFAULT 0,
            `luminance` decimal(6,2) NOT NULL DEFAULT 0.00,
            `luminance_status` varchar(32) NOT NULL DEFAULT \'\',
            `sharpness_score` decimal(8,2) NOT NULL DEFAULT 0.00,
            `sharpness_status` varchar(32) NOT NULL DEFAULT \'\',
            `warnings` text,
            `assessment` varchar(32) NOT NULL DEFAULT \'\',
            `date_add` datetime NOT NULL,
            PRIMARY KEY (`id_visual_inspection`),
            KEY `inspection_id` (`inspection_id`),
            KEY `id_room` (`id_room`),
            KEY `assessment` (`assessment`),
            KEY `date_add` (`date_add`)
        ) ENGINE=' . _MYSQL_ENGINE_ . ' DEFAULT CHARSET=utf8;';

        return (bool) Db::getInstance()->execute($sql);
    }

    /**
     * Install administrative menu tab
     *
     * @return bool
     */
    public function installTab()
    {
        $idParent = (int) Tab::getIdFromClassName('AdminHotelReservationSystemManagement');
        
        $idTab = (int) Tab::getIdFromClassName('AdminVisualInspection');
        $tab = $idTab ? new Tab($idTab) : new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminVisualInspection';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Room Inspection';
        }
        $tab->id_parent = $idParent;
        $tab->module = $this->name;
        return (bool) $tab->save();
    }

    /**
     * Uninstall administrative menu tab
     *
     * @return bool
     */
    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminVisualInspection');
        if ($idTab) {
            $tab = new Tab($idTab);
            return (bool) $tab->delete();
        }
        return true;
    }
}
