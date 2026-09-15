<?php

if (!defined('_PS_VERSION_')) {
    define('_PS_VERSION_', '1.6.1.24');
}
if (!defined('_PS_ROOT_DIR_')) {
    define('_PS_ROOT_DIR_', realpath(__DIR__));
}
if (!defined('_DB_PREFIX_')) {
    define('_DB_PREFIX_', 'ps_');
}

require_once _PS_ROOT_DIR_ . '/config/defines.inc.php';
require_once _PS_ROOT_DIR_ . '/config/autoload.php';
