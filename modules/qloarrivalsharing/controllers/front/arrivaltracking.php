<?php
/**
 * Class QloArrivalSharingArrivalTrackingModuleFrontController
 * Controller Front-Office para acompanhamento e aviso de chegada do hóspede.
 */

if (!defined('_PS_VERSION_')) {
    exit;
}

require_once _PS_MODULE_DIR_ . 'qloarrivalsharing/classes/ArrivalBookingRepository.php';
require_once _PS_MODULE_DIR_ . 'qloarrivalsharing/classes/LocationServiceClient.php';

class QloArrivalSharingArrivalTrackingModuleFrontController extends ModuleFrontController
{
    /** @var LocationServiceClient */
    protected $locationClient;

    /**
     * Inicializa dependências do controller.
     */
    public function __construct()
    {
        parent::__construct();
        $this->locationClient = new LocationServiceClient();
    }

    /**
     * Intercepta requisições AJAX antes da renderização HTML.
     */
    public function postProcess()
    {
        parent::postProcess();

        if (Tools::isSubmit('ajax') && Tools::getValue('action') === 'sendLocation') {
            $this->displayAjaxSendLocation();
        }
    }

    /**
     * Prepara e renderiza a view Smarty do hóspede.
     */
    public function initContent()
    {
        parent::initContent();

        $idOrder = (int) Tools::getValue('id_order');
        $token = Tools::getValue('token');

        // Validação de token contra IDOR
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
     * Endpoint AJAX invocado pelo navegador para enviar as coordenadas pontuais lidas via GPS.
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

        $guestLat = (float) Tools::getValue('lat');
        $guestLng = (float) Tools::getValue('lng');

        // Validação estrita de limites geográficos
        if ($guestLat < -90.0 || $guestLat > 90.0 || $guestLng < -180.0 || $guestLng > 180.0) {
            echo json_encode(array(
                'success' => false,
                'message' => $this->module->l('Coordenadas geográficas fora dos limites válidos.', 'arrivaltracking'),
            ));
            exit;
        }

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

            // Persiste o estado de aproximação para atualizar o painel da recepção em tempo real
            ArrivalBookingRepository::saveArrivalTracking(
                $idOrder,
                $data['current_state'],
                $data['distance_meters'],
                $previousState,
                isset($data['transition']) ? $data['transition'] : 'NO_CHANGE'
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

            echo json_encode(array(
                'success'         => true,
                'inside_geofence' => $isInside,
                'transition'      => $data['transition'],
                'distance_meters' => $distance,
                'alert_triggered' => !empty($data['alert_triggered']),
                'message'         => $msg,
            ));
        } else {
            // Modo de contingência transparente (persiste sinal básico)
            ArrivalBookingRepository::saveArrivalTracking(
                $idOrder,
                'outside',
                0.0,
                $previousState,
                'NO_CHANGE'
            );

            echo json_encode(array(
                'success'         => true,
                'inside_geofence' => false,
                'contingency'     => true,
                'distance_meters' => null,
                'message'         => $this->module->l('Sua aproximação foi comunicada ao hotel. Nossa equipe da recepção aguarda sua chegada!', 'arrivaltracking'),
            ));
        }

        exit;
    }
}
