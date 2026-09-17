<?php
/**
 * Setup and verify all 8 RFC modules in QloApps.
 * Idempotent: can be run on fresh install or existing install.
 */

define('_PS_MODE_DEV_', false);

$configPath = dirname(__DIR__) . '/config/config.inc.php';
if (!file_exists($configPath)) {
    $configPath = '/home/qloapps/www/QloApps/config/config.inc.php';
}

if (!file_exists($configPath)) {
    fwrite(STDERR, "Configuration file not found at: $configPath\n");
    exit(1);
}

require_once $configPath;

echo "--- Initializing QloApps Context ---\n";
$context = Context::getContext();
if (!$context->employee || !Validate::isLoadedObject($context->employee)) {
    $context->employee = new Employee(1);
}
if (!$context->shop || !Validate::isLoadedObject($context->shop)) {
    $context->shop = new Shop(1);
}

$rfcModules = array(
    'qloreservationassistant',
    'qlovisualinspection',
    'qlocontacthealth',
    'qloarrivalsharing',
    'qloreservationpolicy',
    'qloexternalrequests',
    'qloinventoryaudit',
    'qloactionablesearch'
);

echo "--- Checking and Installing RFC Modules ---\n";
foreach ($rfcModules as $moduleName) {
    if (!Module::isInstalled($moduleName)) {
        echo "Installing $moduleName... ";
        $module = Module::getInstanceByName($moduleName);
        if ($module) {
            $installed = $module->install();
            if ($installed) {
                echo "OK\n";
                $module->enable();
            } else {
                echo "FAILED\n";
            }
        } else {
            echo "NOT FOUND\n";
        }
    } else {
        $module = Module::getInstanceByName($moduleName);
        if ($module) {
            $module->enable();
            echo "Module $moduleName is already installed and enabled.\n";
        } else {
            echo "Module $moduleName marked installed but class not loaded.\n";
        }
    }
}

echo "--- Verifying SuperAdmin Tab Permissions ---\n";
Db::getInstance()->execute("
    INSERT IGNORE INTO `" . _DB_PREFIX_ . "access` (`id_profile`, `id_tab`, `view`, `add`, `edit`, `delete`)
    SELECT 1, `id_tab`, 1, 1, 1, 1 FROM `" . _DB_PREFIX_ . "tab`
");

echo "--- Clearing Caches ---\n";
Tools::clearCache();
Tools::clearSmartyCache();
if (file_exists(_PS_CACHE_DIR_ . 'class_index.php')) {
    @unlink(_PS_CACHE_DIR_ . 'class_index.php');
}

echo "--- RFC Modules Setup Complete ---\n";
