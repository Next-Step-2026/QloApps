<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

require_once dirname(__FILE__) . '/../../classes/ArrivalBookingRepository.php';
require_once dirname(__FILE__) . '/../../classes/LocationServiceClient.php';

/**
 * Controlador de administração para recepção e monitoramento de chegadas/traslados.
 */
class AdminArrivalSharingController extends ModuleAdminController
{
    /**
     * @var LocationServiceClient
     */
    protected $locationClient;

    /**
     * Inicializa as configurações do controlador administrativo.
     */
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
        $this->meta_title = $this->l('Recepção e Monitoramento de Traslados');
        $this->override_folder = '';
        $this->locationClient = new LocationServiceClient();
    }

    /**
     * Processa requisições de formulários submetidos no módulo.
     */
    public function postProcess()
    {
        if (Tools::isSubmit('submitGeofenceRadius')) {
            $radius = (float) Tools::getValue('geofence_radius');
            if ($radius <= 0) {
                $this->errors[] = $this->l('O raio de geofence deve ser um valor numérico positivo maior que zero.');
            } else {
                Configuration::updateValue('QLO_ARRIVAL_GEOFENCE_RADIUS', $radius);
                $this->confirmations[] = $this->l('Raio de geofence atualizado com sucesso.');
            }
        }

        parent::postProcess();
    }

    /**
     * Inicializa e renderiza o conteúdo da página do painel de recepção.
     */
    public function initContent()
    {
        parent::initContent();

        $today = date('Y-m-d');
        $idHotel = (int) Tools::getValue('id_hotel', (isset($this->context->cookie->id_hotel) ? $this->context->cookie->id_hotel : 0));
        $arrivals = ArrivalBookingRepository::getTodayArrivals($today, $idHotel ?: null);

        $geofenceRadius = (float) Configuration::get('QLO_ARRIVAL_GEOFENCE_RADIUS');
        if ($geofenceRadius <= 0) {
            $geofenceRadius = 200.0;
        }

        $totalGuests = 0;
        foreach ($arrivals as &$arrival) {
            $totalGuests += (int) $arrival['total_guests'];
            if (!empty($arrival['guest_token'])) {
                $arrival['guest_link'] = $this->context->link->getModuleLink(
                    'qloarrivalsharing',
                    'arrivaltracking',
                    array(
                        'id_order' => (int) $arrival['id_order'],
                        'token'    => $arrival['guest_token'],
                    )
                );
            }
        }
        unset($arrival);

        $hotelCoords = ArrivalBookingRepository::getHotelCoordinates($idHotel ?: null);
        $hotelLat = $hotelCoords['latitude'];
        $hotelLng = $hotelCoords['longitude'];

        $isLocationServiceUp = $this->locationClient->isServiceAvailable();
        $arrivalResult = null;
        $arrivalError = null;

        if (Tools::isSubmit('submitCheckLocation')) {
            $rawGuestLat = Tools::getValue('guest_lat');
            $rawGuestLng = Tools::getValue('guest_lng');

            if (!ArrivalBookingRepository::validateCoordinates($rawGuestLat, $rawGuestLng)) {
                $arrivalError = $this->l('Coordenadas do hóspede ausentes, não numéricas, infinitas ou fora dos limites válidos.');
            } else {
                $inputHotelLat = (float) Tools::getValue('hotel_lat', $hotelLat);
                $inputHotelLng = (float) Tools::getValue('hotel_lng', $hotelLng);
                $guestLat = (float) $rawGuestLat;
                $guestLng = (float) $rawGuestLng;
                $prevState = Tools::getValue('previous_state', 'outside');
                $radius = (float) Tools::getValue('radius', $geofenceRadius);
                $hotelId = Tools::getValue('hotel_id', 'htl-prime-01');

                $payload = array(
                    'hotel_id'          => $hotelId,
                    'hotel_lat'         => $inputHotelLat,
                    'hotel_lng'         => $inputHotelLng,
                    'guest_lat'         => $guestLat,
                    'guest_lng'         => $guestLng,
                    'geofence_radius_m' => $radius,
                    'previous_state'    => in_array($prevState, array('inside', 'outside')) ? $prevState : 'outside',
                );

                $response = $this->locationClient->sendLocationEvent($payload);
                if ($response['success']) {
                    $arrivalResult = $response['data'];
                    if (!isset($arrivalResult['previous_state'])) {
                        $arrivalResult['previous_state'] = $payload['previous_state'];
                    }
                    if (!isset($arrivalResult['geofence_radius_m'])) {
                        $arrivalResult['geofence_radius_m'] = $payload['geofence_radius_m'];
                    }
                } else {
                    $arrivalError = $response['error'];
                }
            }
        }

        $this->context->smarty->assign(array(
            'currentDate'           => Tools::displayDate($today),
            'arrivals'              => $arrivals,
            'totalArrivals'         => count($arrivals),
            'totalGuests'           => $totalGuests,
            'geofenceRadius'        => $geofenceRadius,
            'hotelLat'              => $hotelLat,
            'hotelLng'              => $hotelLng,
            'idHotel'               => $idHotel,
            'locationServiceOnline' => $isLocationServiceUp,
            'arrivalResult'         => $arrivalResult,
            'arrivalError'          => $arrivalError,
            'simGuestLat'           => Tools::getValue('guest_lat', '-8.053100'),
            'simGuestLng'           => Tools::getValue('guest_lng', '-34.886100'),
            'simPrevState'          => Tools::getValue('previous_state', 'outside'),
            'simRadius'             => Tools::getValue('radius', $geofenceRadius),
            'orderAdminLink'        => $this->context->link->getAdminLink('AdminOrders', true),
            'ajaxArrivalStatusUrl'  => $this->context->link->getAdminLink('AdminArrivalSharing', true) . '&ajax=1&action=refresh_arrival_status',
        ));

        $this->setTemplate('reception_dashboard.tpl');
    }

    /**
     * Endpoint AJAX para polling de status das chegadas.
     */
    public function ajaxProcessRefreshArrivalStatus()
    {
        header('Content-Type: application/json; charset=utf-8');

        $today = date('Y-m-d');
        $idHotel = (int) Tools::getValue('id_hotel', (isset($this->context->cookie->id_hotel) ? $this->context->cookie->id_hotel : 0));
        $arrivals = ArrivalBookingRepository::getTodayArrivals($today, $idHotel ?: null);

        $statuses = array();
        foreach ($arrivals as $arrival) {
            $idOrder = (int) $arrival['id_order'];
            $statuses[$idOrder] = array(
                'tracking_state'      => !empty($arrival['tracking_state']) ? $arrival['tracking_state'] : 'waiting',
                'tracking_distance'   => isset($arrival['tracking_distance']) ? (float) $arrival['tracking_distance'] : null,
                'tracking_transition' => !empty($arrival['tracking_transition']) ? $arrival['tracking_transition'] : null,
                'tracking_date_upd'   => !empty($arrival['tracking_date_upd']) ? $arrival['tracking_date_upd'] : null,
            );
        }

        die(json_encode(array(
            'success'  => true,
            'statuses' => $statuses,
        )));
    }

    /**
     * Carrega o template Smarty da visão administrativa do módulo.
     *
     * @return Smarty_Internal_Template
     */
    public function createTemplate($tpl_name)
    {
        $templatePath = _PS_MODULE_DIR_ . $this->module->name . '/views/templates/admin/' . $tpl_name;
        if (file_exists($templatePath)) {
            return $this->context->smarty->createTemplate($templatePath, $this->context->smarty);
        }

        return parent::createTemplate($tpl_name);
    }
}
