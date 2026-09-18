<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

/**
 * Repositório para consulta de reservas de chegada no hotel.
 */
class ArrivalBookingRepository
{
    /**
     * Retorna a lista de chegadas previstas para a data e hotel informados.
     *
     * @return array
     */
    public static function getTodayArrivals($date = null, $idHotel = null)
    {
        $targetDate = $date ? pSQL($date) : date('Y-m-d');

        $sql = new DbQuery();
        $sql->select('
            hbd.`id` AS id_htl_booking_detail,
            hbd.`id_order`,
            o.`reference` AS order_reference,
            hbd.`id_customer`,
            CONCAT(c.`firstname`, " ", c.`lastname`) AS customer_name,
            c.`email` AS customer_email,
            COALESCE(NULLIF(addr.`phone_mobile`, ""), NULLIF(addr.`phone`, ""), "-") AS customer_phone,
            hbd.`id_room`,
            IF(hbd.`room_num` != "", hbd.`room_num`, NULL) AS room_num,
            hbd.`room_type_name`,
            hbd.`id_hotel`,
            hbd.`hotel_name`,
            hbd.`adults`,
            hbd.`children`,
            (hbd.`adults` + hbd.`children`) AS total_guests,
            hbd.`date_from`,
            hbd.`date_to`,
            hbd.`check_in_time`,
            hbd.`id_status`,
            trk.`previous_state` AS tracking_previous_state,
            trk.`current_state` AS tracking_state,
            trk.`transition` AS tracking_transition,
            trk.`distance_meters` AS tracking_distance,
            trk.`date_upd` AS tracking_date_upd
        ');
        $sql->from('htl_booking_detail', 'hbd');
        $sql->innerJoin('orders', 'o', 'o.`id_order` = hbd.`id_order`');
        $sql->innerJoin('customer', 'c', 'c.`id_customer` = hbd.`id_customer`');
        $sql->leftJoin('address', 'addr', 'addr.`id_address` = o.`id_address_delivery`');
        $sql->leftJoin('qlo_arrival_tracking', 'trk', 'trk.`id_order` = hbd.`id_order`');

        $sql->where('hbd.`date_from` >= \'' . $targetDate . ' 00:00:00\'');
        $sql->where('hbd.`date_from` <= \'' . $targetDate . ' 23:59:59\'');
        $sql->where('hbd.`is_refunded` = 0');
        $sql->where('hbd.`is_cancelled` = 0');
        $sql->where('hbd.`id_status` NOT IN (2, 3)');

        if ($idHotel) {
            $sql->where('hbd.`id_hotel` = ' . (int) $idHotel);
        }

        $sql->orderBy('hbd.`check_in_time` ASC, hbd.`id` ASC');

        $results = Db::getInstance(_PS_USE_SQL_SLAVE_)->executeS($sql);

        if (is_array($results)) {
            foreach ($results as &$arrival) {
                $arrival['guest_token'] = self::generateGuestToken((int) $arrival['id_order']);
            }
            unset($arrival);
        }

        return is_array($results) ? $results : array();
    }

    /**
     * Retorna as coordenadas geográficas do hotel com fallback padrão.
     *
     * @return array
     */
    public static function getHotelCoordinates($idHotel = null)
    {
        $defaultCoords = array(
            'latitude'  => -8.052240,
            'longitude' => -34.885650,
        );

        if (!$idHotel) {
            return $defaultCoords;
        }

        $sql = new DbQuery();
        $sql->select('`latitude`, `longitude`');
        $sql->from('htl_branch_info');
        $sql->where('`id` = ' . (int) $idHotel);

        $row = Db::getInstance(_PS_USE_SQL_SLAVE_)->getRow($sql);
        if ($row && isset($row['latitude'], $row['longitude']) && self::validateCoordinates($row['latitude'], $row['longitude'])) {
            return array(
                'latitude'  => (float) $row['latitude'],
                'longitude' => (float) $row['longitude'],
            );
        }

        return $defaultCoords;
    }

    /**
     * Gera o token de acesso à tela do hóspede.
     *
     * @return string
     */
    public static function generateGuestToken($idOrder)
    {
        return substr(md5('qloarrival_' . (int) $idOrder . '_' . _COOKIE_KEY_), 0, 16);
    }

    /**
     * Valida o token de acesso do hóspede contra timing attacks.
     *
     * @return bool
     */
    public static function validateGuestToken($idOrder, $token)
    {
        if (empty($token) || (int) $idOrder <= 0) {
            return false;
        }

        return hash_equals(self::generateGuestToken((int) $idOrder), (string) $token);
    }

    /**
     * Valida presença, formato numérico, finitude e limites geodésicos de coordenadas.
     *
     * @return bool
     */
    public static function validateCoordinates($rawLat, $rawLng)
    {
        if ($rawLat === false || $rawLng === false || $rawLat === null || $rawLat === '' || $rawLng === null || $rawLng === '') {
            return false;
        }

        if (!is_numeric($rawLat) || !is_numeric($rawLng)) {
            return false;
        }

        $lat = (float) $rawLat;
        $lng = (float) $rawLng;

        return (is_finite($lat) && is_finite($lng) && $lat >= -90.0 && $lat <= 90.0 && $lng >= -180.0 && $lng <= 180.0);
    }

    /**
     * Obtém informações públicas da reserva para a tela do hóspede.
     *
     * @return array|false
     */
    public static function getBookingForGuest($idOrder)
    {
        $idOrder = (int) $idOrder;
        if ($idOrder <= 0) {
            return false;
        }

        $sql = new DbQuery();
        $sql->select('
            hbd.`id_order`,
            o.`reference` AS order_reference,
            c.`firstname`,
            c.`lastname`,
            hbd.`id_hotel`,
            hbd.`hotel_name`,
            hbd.`room_type_name`,
            hbd.`date_from`,
            hbd.`date_to`,
            hbd.`check_in_time`
        ');
        $sql->from('htl_booking_detail', 'hbd');
        $sql->innerJoin('orders', 'o', 'o.`id_order` = hbd.`id_order`');
        $sql->innerJoin('customer', 'c', 'c.`id_customer` = hbd.`id_customer`');
        $sql->where('hbd.`id_order` = ' . (int) $idOrder);
        $sql->where('hbd.`is_refunded` = 0');
        $sql->where('hbd.`is_cancelled` = 0');
        $sql->where('hbd.`id_status` NOT IN (2, 3)');

        $row = Db::getInstance(_PS_USE_SQL_SLAVE_)->getRow($sql);
        if (!$row) {
            return false;
        }

        $lastNameInitial = !empty($row['lastname']) ? mb_substr(trim($row['lastname']), 0, 1, 'UTF-8') . '.' : '';
        $row['guest_display_name'] = trim($row['firstname'] . ' ' . $lastNameInitial);

        return $row;
    }

    /**
     * Cria a tabela de rastreamento de chegadas na instalação do módulo.
     *
     * @return bool
     */
    public static function createTrackingTable()
    {
        $sql = 'CREATE TABLE IF NOT EXISTS `' . _DB_PREFIX_ . 'qlo_arrival_tracking` (
            `id_order` INT(10) UNSIGNED NOT NULL,
            `previous_state` VARCHAR(16) NOT NULL DEFAULT "outside",
            `current_state` VARCHAR(16) NOT NULL DEFAULT "outside",
            `transition` VARCHAR(16) NOT NULL DEFAULT "NO_CHANGE",
            `distance_meters` DECIMAL(10,2) NULL,
            `date_upd` DATETIME NOT NULL,
            PRIMARY KEY (`id_order`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8';

        return Db::getInstance()->execute($sql);
    }

    /**
     * Remove a tabela de rastreamento de chegadas na desinstalação do módulo.
     *
     * @return bool
     */
    public static function dropTrackingTable()
    {
        return Db::getInstance()->execute('DROP TABLE IF EXISTS `' . _DB_PREFIX_ . 'qlo_arrival_tracking`');
    }


    /**
     * Normaliza a transição para evitar disparos duplicados sob concorrência.
     *
     * @param string $existingState
     * @param string $incomingState
     * @param string $incomingTransition
     * @return string
     */
    public static function resolveSafeTransition($existingState, $incomingState, $incomingTransition)
    {
        if ($existingState === 'inside' && $incomingState === 'inside' && $incomingTransition === 'ENTERED') {
            return 'NO_CHANGE';
        }
        if ($existingState === 'outside' && $incomingState === 'outside' && $incomingTransition === 'EXITED') {
            return 'NO_CHANGE';
        }

        return in_array($incomingTransition, array('ENTERED', 'EXITED', 'NO_CHANGE')) ? $incomingTransition : 'NO_CHANGE';
    }

    /**
     * Adquire lock exclusivo para processamento concorrente da reserva.
     *
     * @param int $idOrder
     * @param int $timeout
     * @return bool
     */
    public static function acquireOrderLock($idOrder, $timeout = 3)
    {
        $idOrder = (int) $idOrder;
        if ($idOrder <= 0) {
            return false;
        }

        $timeout = max(0, (int) $timeout);
        $lockName = 'qlo_arrival_order_' . $idOrder;
        $sql = 'SELECT GET_LOCK(\'' . pSQL($lockName) . '\', ' . $timeout . ')';

        return (int) Db::getInstance()->getValue($sql, false) === 1;
    }

    /**
     * Libera o lock exclusivo da reserva.
     *
     * @param int $idOrder
     * @return bool
     */
    public static function releaseOrderLock($idOrder)
    {
        $idOrder = (int) $idOrder;
        if ($idOrder <= 0) {
            return false;
        }

        $lockName = 'qlo_arrival_order_' . $idOrder;
        $sql = 'SELECT RELEASE_LOCK(\'' . pSQL($lockName) . '\')';

        return (int) Db::getInstance()->getValue($sql, false) === 1;
    }

    /**
     * Salva ou atualiza o estado de aproximação de uma reserva no banco.
     *
     * @return bool
     */
    public static function saveArrivalTracking($idOrder, $currentState, $distance, $previousState = 'outside', $transition = 'NO_CHANGE')
    {
        $idOrder = (int) $idOrder;
        if ($idOrder <= 0) {
            return false;
        }

        $currentState = in_array($currentState, array('inside', 'outside')) ? $currentState : 'outside';
        $previousState = in_array($previousState, array('inside', 'outside')) ? $previousState : 'outside';
        $transition = in_array($transition, array('ENTERED', 'EXITED', 'NO_CHANGE')) ? $transition : 'NO_CHANGE';
        $distance = (float) $distance;

        $sql = 'INSERT INTO `' . _DB_PREFIX_ . 'qlo_arrival_tracking`
                (`id_order`, `previous_state`, `current_state`, `transition`, `distance_meters`, `date_upd`)
                VALUES (' . $idOrder . ', \'' . pSQL($previousState) . '\', \'' . pSQL($currentState) . '\', \'' . pSQL($transition) . '\', ' . $distance . ', NOW())
                ON DUPLICATE KEY UPDATE
                    `previous_state` = IF(`current_state` = \'inside\' AND VALUES(`current_state`) = \'inside\', \'inside\', VALUES(`previous_state`)),
                    `transition` = IF(`current_state` = \'inside\' AND VALUES(`current_state`) = \'inside\', \'NO_CHANGE\', VALUES(`transition`)),
                    `current_state` = VALUES(`current_state`),
                    `distance_meters` = VALUES(`distance_meters`),
                    `date_upd` = NOW()';

        return Db::getInstance()->execute($sql);
    }

    /**
     * Retorna o último estado registrado de aproximação de uma reserva.
     *
     * @return array|false
     */
    public static function getArrivalTrackingState($idOrder)
    {
        $idOrder = (int) $idOrder;
        if ($idOrder <= 0) {
            return false;
        }

        $sql = 'SELECT `previous_state`, `current_state`, `transition`, `distance_meters`, `date_upd`
                FROM `' . _DB_PREFIX_ . 'qlo_arrival_tracking`
                WHERE `id_order` = ' . $idOrder;

        return Db::getInstance(_PS_USE_SQL_SLAVE_)->getRow($sql);
    }
}
