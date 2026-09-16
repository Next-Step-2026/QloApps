<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

require_once _PS_MODULE_DIR_ . 'qloarrivalsharing/classes/ArrivalBookingRepository.php';
require_once _PS_MODULE_DIR_ . 'qloarrivalsharing/classes/LocationServiceClient.php';

/**
 * Acompanhamento de chegada do hóspede.
 */
class QloArrivalSharingArrivalTrackingModuleFrontController extends ModuleFrontController
{
    /** @var LocationServiceClient */
    protected $locationClient;

    /**
     * Inicializa o cliente de localização.
     */
    public function __construct()
    {
        parent::__construct();
        $this->locationClient = new LocationServiceClient();
    }

    /**
     * Processa as requisições AJAX do controller.
     */
    public function postProcess()
    {
        parent::postProcess();

        if (Tools::isSubmit('ajax') && Tools::getValue('action') === 'sendLocation') {
            $this->displayAjaxSendLocation();
        }
    }

    /**
     * Renderiza a tela do hóspede.
     */
    public function initContent()
    {
        parent::initContent();

        $idOrder = (int) Tools::getValue('id_order');
        $token = Tools::getValue('token');

        if (!ArrivalBookingRepository::validateGuestToken($idOrder, $token)) {
            $this->context->smarty->assign(array(
                'hasError'     => true,
                'errorMessage' => $this->module->l('Link de acompanhamento inválido ou expirado.', 'arrivaltracking'),
            ));
            $this->setTemplate('guest_arrival.tpl');
            return;
        }

        $booking = ArrivalBookingRepository::getBookingForGuest($idOrder);
        if (!$booking) {
            $this->context->smarty->assign(array(
                'hasError'     => true,
                'errorMessage' => $this->module->l('Reserva não encontrada ou já encerrada.', 'arrivaltracking'),
            ));
            $this->setTemplate('guest_arrival.tpl');
            return;
        }

        $hotelCoords = ArrivalBookingRepository::getHotelCoordinates($booking['id_hotel']);
        $geofenceRadius = (float) Configuration::get('QLO_ARRIVAL_GEOFENCE_RADIUS');
        if ($geofenceRadius <= 0) {
            $geofenceRadius = 200.0;
        }

        $ajaxUrl = $this->context->link->getModuleLink(
            'qloarrivalsharing',
            'arrivaltracking',
            array('ajax' => 1, 'action' => 'sendLocation')
        );

        $this->context->smarty->assign(array(
            'hasError'       => false,
            'booking'        => $booking,
            'hotelCoords'    => $hotelCoords,
            'geofenceRadius' => $geofenceRadius,
            'idOrder'        => $idOrder,
            'token'          => $token,
            'ajaxUrl'        => $ajaxUrl,
        ));

        $this->setTemplate('guest_arrival.tpl');
    }

    /**
     * Recebe e processa a localização enviada pelo hóspede via AJAX.
     */
    public function displayAjaxSendLocation()
    {
        header('Content-Type: application/json; charset=utf-8');

        $idOrder = (int) Tools::getValue('id_order');
        $token = Tools::getValue('token');

        if (!ArrivalBookingRepository::validateGuestToken($idOrder, $token)) {
            echo json_encode(array(
                'success' => false,
                'message' => $this->module->l('Acesso não autorizado ou token expirado.', 'arrivaltracking'),
            ));
            exit;
        }

        $booking = ArrivalBookingRepository::getBookingForGuest($idOrder);
        if (!$booking) {
            echo json_encode(array(
                'success' => false,
                'message' => $this->module->l('Reserva inválida ou não encontrada.', 'arrivaltracking'),
            ));
            exit;
        }

        $rawLat = Tools::getValue('lat');
        $rawLng = Tools::getValue('lng');

        if (!ArrivalBookingRepository::validateCoordinates($rawLat, $rawLng)) {
            echo json_encode(array(
                'success' => false,
                'message' => $this->module->l('Coordenadas geográficas ausentes ou em formato inválido.', 'arrivaltracking'),
            ));
            exit;
        }

        $guestLat = (float) $rawLat;
        $guestLng = (float) $rawLng;

        if (!ArrivalBookingRepository::acquireOrderLock($idOrder, 3)) {
            echo json_encode(array(
                'success' => false,
                'message' => $this->module->l('Já existe uma leitura de localização em processamento para esta reserva. Tente novamente em instantes.', 'arrivaltracking'),
            ));
            exit;
        }

        try {
            $hotelCoords = ArrivalBookingRepository::getHotelCoordinates($booking['id_hotel']);
            $geofenceRadius = (float) Configuration::get('QLO_ARRIVAL_GEOFENCE_RADIUS');
            if ($geofenceRadius <= 0) {
                $geofenceRadius = 200.0;
            }

            $tracking = ArrivalBookingRepository::getArrivalTrackingState($idOrder);
            $previousState = (!empty($tracking) && !empty($tracking['current_state'])) ? $tracking['current_state'] : 'outside';

            $payload = array(
                'hotel_id'          => 'htl-' . (int) $booking['id_hotel'],
                'hotel_lat'         => (float) $hotelCoords['latitude'],
                'hotel_lng'         => (float) $hotelCoords['longitude'],
                'guest_lat'         => $guestLat,
                'guest_lng'         => $guestLng,
                'geofence_radius_m' => $geofenceRadius,
                'previous_state'    => $previousState,
            );

            $response = $this->locationClient->sendLocationEvent($payload);

            if ($response['success']) {
                $data = $response['data'];
                $isInside = ($data['current_state'] === 'inside');
                $distance = round($data['distance_meters'], 1);
                $rawTransition = isset($data['transition']) ? $data['transition'] : 'NO_CHANGE';
                $safeTransition = ArrivalBookingRepository::resolveSafeTransition($previousState, $data['current_state'], $rawTransition);

                ArrivalBookingRepository::saveArrivalTracking(
                    $idOrder,
                    $data['current_state'],
                    $data['distance_meters'],
                    $previousState,
                    $safeTransition
                );

                if ($isInside) {
                    $msg = sprintf(
                        $this->module->l('Você chegou às imediações do hotel (aproximadamente %s metros)! Nossa equipe da recepção foi notificada para recebê-lo.', 'arrivaltracking'),
                        $distance
                    );
                } else {
                    $msg = sprintf(
                        $this->module->l('Sua posição foi recebida! Você está a cerca de %s metros do hotel. Quando estiver mais próximo (%s m), você pode avisar novamente.', 'arrivaltracking'),
                        $distance,
                        (int) $geofenceRadius
                    );
                }

                $output = array(
                    'success'         => true,
                    'inside_geofence' => $isInside,
                    'transition'      => $safeTransition,
                    'distance_meters' => $distance,
                    'alert_triggered' => ($safeTransition === 'ENTERED'),
                    'message'         => $msg,
                );
            } else {
                $errorMessage = !empty($response['error'])
                    ? $response['error']
                    : $this->module->l('Serviço de cálculo de proximidade temporariamente indisponível.', 'arrivaltracking');

                $output = array(
                    'success' => false,
                    'message' => $errorMessage,
                );
            }
        } finally {
            ArrivalBookingRepository::releaseOrderLock($idOrder);
        }

        echo json_encode($output);
        exit;
    }
}
