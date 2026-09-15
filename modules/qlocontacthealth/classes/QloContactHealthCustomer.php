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

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloContactHealthCustomer extends ObjectModel
{
    /** @var int Customer ID */
    public $id_customer;

    /** @var string Last verification date */
    public $last_verified_at;

    /** @var string LGPD consent expiration date */
    public $consent_expires_at;

    /** @var string Record creation date */
    public $date_add;

    /** @var string Record update date */
    public $date_upd;

    /**
     * @see ObjectModel::$definition
     */
    public static $definition = array(
        'table' => 'qlocontacthealth_customer',
        'primary' => 'id_customer',
        'fields' => array(
            'id_customer' => array('type' => self::TYPE_INT, 'validate' => 'isUnsignedId', 'required' => true),
            'last_verified_at' => array('type' => self::TYPE_DATE, 'validate' => 'isDate', 'allow_null' => true),
            'consent_expires_at' => array('type' => self::TYPE_DATE, 'validate' => 'isDate', 'allow_null' => true),
            'date_add' => array('type' => self::TYPE_DATE, 'validate' => 'isDate'),
            'date_upd' => array('type' => self::TYPE_DATE, 'validate' => 'isDate'),
        ),
    );

    /**
     * Ensures the database table exists.
     *
     * @return bool
     */
    public static function createTable()
    {
        $sql = 'CREATE TABLE IF NOT EXISTS `' . _DB_PREFIX_ . 'qlocontacthealth_customer` (
            `id_customer` INT(10) UNSIGNED NOT NULL,
            `last_verified_at` DATETIME NULL DEFAULT NULL,
            `consent_expires_at` DATETIME NULL DEFAULT NULL,
            `date_add` DATETIME NOT NULL,
            `date_upd` DATETIME NOT NULL,
            PRIMARY KEY (`id_customer`)
        ) ENGINE=' . _MYSQL_ENGINE_ . ' DEFAULT CHARSET=utf8;';

        return Db::getInstance()->execute($sql);
    }

    /**
     * Drops database table.
     *
     * @return bool
     */
    public static function dropTable()
    {
        $sql = 'DROP TABLE IF EXISTS `' . _DB_PREFIX_ . 'qlocontacthealth_customer`;';

        return Db::getInstance()->execute($sql);
    }

    /**
     * Retrieves health record array for a customer ID.
     *
     * @param int $idCustomer
     * @return array|bool
     */
    public static function getByCustomerId($idCustomer)
    {
        $idCustomer = (int) $idCustomer;
        if (!$idCustomer) {
            return false;
        }

        self::createTable();

        $sql = 'SELECT * FROM `' . _DB_PREFIX_ . 'qlocontacthealth_customer` WHERE `id_customer` = ' . $idCustomer;
        return Db::getInstance()->getRow($sql);
    }

    /**
     * Records/updates verification timestamp for a customer.
     *
     * @param int $idCustomer
     * @return bool
     */
    public static function recordVerification($idCustomer)
    {
        $idCustomer = (int) $idCustomer;
        if (!$idCustomer) {
            return false;
        }

        self::createTable();

        $now = date('Y-m-d H:i:s');
        $sql = 'INSERT INTO `' . _DB_PREFIX_ . 'qlocontacthealth_customer` (`id_customer`, `last_verified_at`, `date_add`, `date_upd`)
                VALUES (' . $idCustomer . ', \'' . pSQL($now) . '\', \'' . pSQL($now) . '\', \'' . pSQL($now) . '\')
                ON DUPLICATE KEY UPDATE `last_verified_at` = \'' . pSQL($now) . '\', `date_upd` = \'' . pSQL($now) . '\'';

        return Db::getInstance()->execute($sql);
    }

    /**
     * Updates consent expiration timestamp for a customer (or null to clear/expire).
     *
     * @param int $idCustomer
     * @param string|null $dateStr
     * @return bool
     */
    public static function updateConsentExpiration($idCustomer, $dateStr = null)
    {
        $idCustomer = (int) $idCustomer;
        if (!$idCustomer) {
            return false;
        }

        self::createTable();
        $now = date('Y-m-d H:i:s');
        $val = (!empty($dateStr) && $dateStr !== '0000-00-00 00:00:00') ? '\'' . pSQL($dateStr) . '\'' : 'NULL';

        $sql = 'INSERT INTO `' . _DB_PREFIX_ . 'qlocontacthealth_customer` (`id_customer`, `consent_expires_at`, `date_add`, `date_upd`)
                VALUES (' . $idCustomer . ', ' . $val . ', \'' . pSQL($now) . '\', \'' . pSQL($now) . '\')
                ON DUPLICATE KEY UPDATE `consent_expires_at` = ' . $val . ', `date_upd` = \'' . pSQL($now) . '\'';

        return Db::getInstance()->execute($sql);
    }
}
