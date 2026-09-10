<?php
/**
 * Admin Controller for Visual Inspection and Housekeeping Quality Evaluation
 *
 * @author    QloApps Engineering
 * @copyright Since 2026 QloApps
 * @license   https://opensource.org/licenses/AFL-3.0 Academic Free License 3.0 (AFL-3.0)
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

class AdminVisualInspectionController extends ModuleAdminController
{
    const PYTHON_SERVICE_URL = 'http://127.0.0.1:8102/v1/visual-inspections';
    const CURL_TIMEOUT_MS = 800;
    const MAX_FILE_SIZE_BYTES = 5242880; // 5 MB

    public function __construct()
    {
        $this->bootstrap = true;
        $this->override_folder = '';
        parent::__construct();
    }

    const INSPECTION_ITEMS = [
        'bed' => [
            'field' => 'photo_bed',
            'title' => '1. Bed & Linen',
            'icon'  => 'icon-bookmark',
        ],
        'bath' => [
            'field' => 'photo_bath',
            'title' => '2. Sanitized Bathroom',
            'icon'  => 'icon-tint',
        ],
        'amenities' => [
            'field' => 'photo_amenities',
            'title' => '3. Replenished Amenities',
            'icon'  => 'icon-gift',
        ],
    ];

    /**
     * Main action to render and process the room inspection form
     */
    public function initContent()
    {
        parent::initContent();

        $itemsResults = [];
        $errorMessage = null;
        $selectedRoomId = null;
        $overallAssessment = 'EVIDENCE_VALID';

        if (Tools::isSubmit('submitInspection')) {
            $selectedRoomId = Tools::getValue('room_id');
            $allowedMimes = ['image/jpeg', 'image/png', 'image/pjpeg', 'image/x-png'];
            $hasAnyError = false;

            foreach (self::INSPECTION_ITEMS as $itemKey => $itemConfig) {
                $fieldName = $itemConfig['field'];
                $itemResult = null;
                $itemError = null;
                $savedImagePath = null;

                if (!isset($_FILES[$fieldName]) || $_FILES[$fieldName]['error'] !== UPLOAD_ERR_OK) {
                    $itemError = $this->l('Photo not provided for this item.');
                    $hasAnyError = true;
                    $overallAssessment = 'EVIDENCE_REQUIRES_RETAKE';
                } else {
                    $tmpFilePath = $_FILES[$fieldName]['tmp_name'];
                    $fileSize = (int) $_FILES[$fieldName]['size'];

                    if ($fileSize > self::MAX_FILE_SIZE_BYTES) {
                        $itemError = $this->l('Photo exceeds the maximum allowed size of 5 MB.');
                        $hasAnyError = true;
                        $overallAssessment = 'EVIDENCE_REQUIRES_RETAKE';
                    } else {
                        $mimeType = function_exists('mime_content_type') ? mime_content_type($tmpFilePath) : $_FILES[$fieldName]['type'];

                        if (!in_array($mimeType, $allowedMimes)) {
                            $itemError = $this->l('Invalid format. Only JPEG or PNG images are allowed.');
                            $hasAnyError = true;
                            $overallAssessment = 'EVIDENCE_REQUIRES_RETAKE';
                        } else {
                            $subInspectionId = 'INSP-' . date('YmdHis') . '-' . preg_replace('/[^a-zA-Z0-9_-]/', '', (string)$selectedRoomId) . '-' . $itemKey;
                            $correlationId = Tools::passwdGen(16, 'ALPHANUMERIC');

                            $cFile = new CURLFile($tmpFilePath, $mimeType, $_FILES[$fieldName]['name']);
                            $postData = [
                                'file'          => $cFile,
                                'room_id'       => (string) $selectedRoomId,
                                'inspection_id' => $subInspectionId,
                            ];

                            $ch = curl_init(self::PYTHON_SERVICE_URL);
                            curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                            curl_setopt($ch, CURLOPT_POST, true);
                            curl_setopt($ch, CURLOPT_POSTFIELDS, $postData);
                            curl_setopt($ch, CURLOPT_TIMEOUT_MS, self::CURL_TIMEOUT_MS);
                            curl_setopt($ch, CURLOPT_HTTPHEADER, [
                                'X-Correlation-ID: ' . $correlationId,
                            ]);

                            $response = curl_exec($ch);
                            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                            curl_close($ch);

                            if ($response && $httpCode === 200) {
                                $itemResult = json_decode($response, true);
                                if (isset($itemResult['assessment']) && $itemResult['assessment'] !== 'EVIDENCE_VALID') {
                                    $overallAssessment = 'EVIDENCE_REQUIRES_RETAKE';
                                }
                            } elseif ($httpCode === 400) {
                                $errJson = json_decode($response, true);
                                $itemError = !empty($errJson['detail']) ? $errJson['detail'] : $this->l('Image rejected by quality evaluator.');
                                $hasAnyError = true;
                                $overallAssessment = 'EVIDENCE_REQUIRES_RETAKE';
                            } else {
                                $itemError = $this->l('Automated metrics unavailable (Local inspection service offline).');
                            }

                            // Save evidence file permanently and persist in database
                            $savedImagePath = $this->saveEvidenceImage($tmpFilePath, $subInspectionId, $mimeType);
                            if (!$savedImagePath) {
                                $itemError = $this->l('Could not save image to disk due to a storage or permission error.');
                                $hasAnyError = true;
                                $overallAssessment = 'EVIDENCE_REQUIRES_RETAKE';
                            } else {
                                $this->saveInspectionRecord(
                                    $subInspectionId,
                                    $selectedRoomId,
                                    $itemKey,
                                    $itemConfig['title'],
                                    $savedImagePath,
                                    $itemResult
                                );
                            }
                        }
                    }
                }

                $itemsResults[$itemKey] = [
                    'title'         => $itemConfig['title'],
                    'icon'          => $itemConfig['icon'],
                    'preview_image' => $savedImagePath,
                    'result'        => $itemResult,
                    'error'         => $itemError,
                ];
            }

            if ($hasAnyError) {
                $errorMessage = $this->l('One or more photo evidences failed quality checks or require a retake.');
            }
        }

        $this->context->smarty->assign([
            'itemsResults'      => $itemsResults,
            'overallAssessment' => $overallAssessment,
            'inspectionError'   => $errorMessage,
            'selectedRoomId'    => $selectedRoomId,
            'roomsList'         => $this->getHotelRoomsList(),
            'recentInspections' => $this->getRecentInspections(),
            'moduleImgUri'      => __PS_BASE_URI__ . 'modules/' . $this->module->name . '/views/img/inspections/',
        ]);

        $this->template = 'content.tpl';
        $this->content .= $this->context->smarty->fetch($this->getTemplatePath() . 'inspection_form.tpl');
        $this->context->smarty->assign('content', $this->content);
    }

    /**
     * Persist uploaded image file to module storage directory
     *
     * @param string $tmpPath
     * @param string $subInspectionId
     * @param string $mimeType
     * @return string|false
     */
     protected function saveEvidenceImage($tmpPath, $subInspectionId, $mimeType)
     {
         $ext = ($mimeType === 'image/png' || $mimeType === 'image/x-png') ? 'png' : 'jpg';
         $filename = $subInspectionId . '.' . $ext;
         $targetDir = _PS_MODULE_DIR_ . $this->module->name . '/views/img/inspections/';

         if (!is_dir($targetDir) && !@mkdir($targetDir, 0755, true) && !is_dir($targetDir)) {
             PrestaShopLogger::addLog(
                 sprintf('[VisualInspection] Failed to create destination directory: %s', $targetDir),
                 3,
                 null,
                 'VisualInspection',
                 isset($this->context->employee->id) ? (int) $this->context->employee->id : null
             );
             return false;
         }

         $targetPath = $targetDir . $filename;
         if (!@copy($tmpPath, $targetPath) || !file_exists($targetPath)) {
             PrestaShopLogger::addLog(
                 sprintf('[VisualInspection] Failed to copy evidence image to: %s', $targetPath),
                 3,
                 null,
                 'VisualInspection',
                 isset($this->context->employee->id) ? (int) $this->context->employee->id : null
             );
             return false;
         }

         return $filename;
     }

    /**
     * Insert inspection record into qlo_visual_inspection table
     *
     * @param string $inspectionId
     * @param string $selectedRoomId
     * @param string $itemKey
     * @param string $itemTitle
     * @param string $imagePath
     * @param array|null $result
     * @return bool
     */
    protected function saveInspectionRecord($inspectionId, $selectedRoomId, $itemKey, $itemTitle, $imagePath, $result)
    {
        $idRoom = (int) str_replace('room-', '', (string) $selectedRoomId);
        $roomNum = (string) $selectedRoomId;

        $idEmployee = isset($this->context->employee->id) ? (int) $this->context->employee->id : 0;
        $employeeName = isset($this->context->employee) ? trim($this->context->employee->firstname . ' ' . $this->context->employee->lastname) : '';

        $width = isset($result['metrics']['width']) ? (int) $result['metrics']['width'] : 0;
        $height = isset($result['metrics']['height']) ? (int) $result['metrics']['height'] : 0;
        $luminance = isset($result['metrics']['luminance']) ? (float) $result['metrics']['luminance'] : 0.0;
        $lumStatus = isset($result['metrics']['luminance_status']) ? pSQL($result['metrics']['luminance_status']) : '';
        $sharpness = isset($result['metrics']['sharpness_score']) ? (float) $result['metrics']['sharpness_score'] : 0.0;
        $sharpStatus = isset($result['metrics']['sharpness_status']) ? pSQL($result['metrics']['sharpness_status']) : '';
        $warnings = (isset($result['warnings']) && is_array($result['warnings'])) ? pSQL(json_encode($result['warnings'])) : '';
        $assessment = isset($result['assessment']) ? pSQL($result['assessment']) : 'EVIDENCE_REQUIRES_RETAKE';

        $data = [
            'inspection_id'    => pSQL($inspectionId),
            'id_room'          => (int) $idRoom,
            'room_num'         => pSQL($roomNum),
            'id_employee'      => (int) $idEmployee,
            'employee_name'    => pSQL($employeeName),
            'item_key'         => pSQL($itemKey),
            'item_title'       => pSQL($itemTitle),
            'image_path'       => pSQL($imagePath),
            'width'            => (int) $width,
            'height'           => (int) $height,
            'luminance'        => (float) $luminance,
            'luminance_status' => $lumStatus,
            'sharpness_score'  => (float) $sharpness,
            'sharpness_status' => $sharpStatus,
            'warnings'         => $warnings,
            'assessment'       => $assessment,
            'date_add'         => date('Y-m-d H:i:s'),
        ];

        return Db::getInstance()->insert('visual_inspection', $data);
    }

    /**
     * Retrieve recent inspection logs for General Manager audit view
     *
     * @param int $limit
     * @return array
     */
    protected function getRecentInspections($limit = 30)
    {
        $inspections = [];

        try {
            $idLang = (int) (isset($this->context->language->id) ? $this->context->language->id : Configuration::get('PS_LANG_DEFAULT'));

            $sql = 'SELECT vi.*,
                           r.`room_num` AS physical_room_num,
                           r.`floor` AS room_floor,
                           pl.`name` AS room_type_name,
                           hbil.`hotel_name` AS hotel_name
                    FROM `' . _DB_PREFIX_ . 'visual_inspection` vi
                    LEFT JOIN `' . _DB_PREFIX_ . 'htl_room_information` r ON (r.`id` = vi.`id_room`)
                    LEFT JOIN `' . _DB_PREFIX_ . 'product_lang` pl ON (pl.`id_product` = r.`id_product` AND pl.`id_lang` = ' . (int) $idLang . ')
                    LEFT JOIN `' . _DB_PREFIX_ . 'htl_branch_info_lang` hbil ON (hbil.`id` = r.`id_hotel` AND hbil.`id_lang` = ' . (int) $idLang . ')
                    ORDER BY vi.`id_visual_inspection` DESC
                    LIMIT ' . (int) $limit;

            $rows = Db::getInstance()->executeS($sql);

            if (!empty($rows)) {
                foreach ($rows as $row) {
                    $row['warnings_list'] = !empty($row['warnings']) ? json_decode($row['warnings'], true) : [];

                    // Determine user-friendly display name while keeping raw ID intact
                    if (!empty($row['physical_room_num'])) {
                        $row['display_room_num'] = $row['physical_room_num'];
                    } else {
                        $row['display_room_num'] = !empty($row['room_num']) ? $row['room_num'] : '#' . $row['id_room'];
                    }

                    $row['display_room_type'] = !empty($row['room_type_name']) ? $row['room_type_name'] : '';
                    $row['display_floor'] = !empty($row['room_floor']) ? $row['room_floor'] : '';

                    $fullTitleParts = [];
                    $fullTitleParts[] = $row['display_room_num'];
                    if (!empty($row['display_room_type'])) {
                        $fullTitleParts[] = $row['display_room_type'];
                    }
                    if (!empty($row['display_floor'])) {
                        $fullTitleParts[] = '(' . $row['display_floor'] . ')';
                    }
                    if (!empty($row['hotel_name'])) {
                        $fullTitleParts[] = '[' . $row['hotel_name'] . ']';
                    }
                    $row['full_room_title'] = implode(' - ', $fullTitleParts);

                    $inspections[] = $row;
                }
            }
        } catch (Exception $e) {
            PrestaShopLogger::addLog($e->getMessage(), 3);
        }

        return $inspections;
    }

    /**
     * Retrieve active hotel rooms from database or fallback list
     *
     * @return array
     */
    protected function getHotelRoomsList()
    {
        $rooms = [];

        try {
            if (class_exists('Db')) {
                $idLang = (int) (isset($this->context->language->id) ? $this->context->language->id : Configuration::get('PS_LANG_DEFAULT'));

                $sql = 'SELECT r.`id` AS id_room, r.`room_num`, r.`id_status`, r.`floor`,
                               pl.`name` AS room_type_name
                        FROM `' . _DB_PREFIX_ . 'htl_room_information` r
                        LEFT JOIN `' . _DB_PREFIX_ . 'product_lang` pl ON (pl.`id_product` = r.`id_product` AND pl.`id_lang` = ' . (int) $idLang . ')
                        ORDER BY r.`room_num` ASC LIMIT 50';
                $dbRooms = Db::getInstance()->executeS($sql);

                if (!empty($dbRooms)) {
                    foreach ($dbRooms as $row) {
                        $nameParts = [];
                        $nameParts[] = 'Room ' . $row['room_num'];
                        if (!empty($row['room_type_name'])) {
                            $nameParts[] = '(' . $row['room_type_name'] . ')';
                        }
                        if (!empty($row['floor'])) {
                            $nameParts[] = '- Floor ' . $row['floor'];
                        }

                        $rooms[] = [
                            'id'   => 'room-' . (int) $row['id_room'],
                            'name' => implode(' ', $nameParts),
                        ];
                    }
                }
            }
        } catch (Exception $e) {
            PrestaShopLogger::addLog($e->getMessage(), 3);
        }

        if (empty($rooms)) {
            $rooms = [
                ['id' => 'room-101', 'name' => 'Room 101 (Standard)'],
                ['id' => 'room-102', 'name' => 'Room 102 (Deluxe)'],
                ['id' => 'room-201', 'name' => 'Room 201 (Presidential Suite)'],
                ['id' => 'room-202', 'name' => 'Room 202 (Executive)'],
            ];
        }

        return $rooms;
    }
}
