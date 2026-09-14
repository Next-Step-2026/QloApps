<?php

if (!defined('_PS_VERSION_')) {
    exit;
}

/**
 * Class ArrivalBookingRepository
 * Repositório para consulta de reservas de chegada no hotel.
 */
class ArrivalBookingRepository
{
    /**
     * Retorna a lista de chegadas previstas para a data e hotel informados.
     *
     * @param string|null $date Data no formato Y-m-d (padrão: hoje se null)
     * @param int|null $idHotel ID do hotel para filtragem (opcional)
     * @return array Lista de reservas de chegada
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
            hbd.`id_status`
        ');
        $sql->from('htl_booking_detail', 'hbd');
        $sql->innerJoin('orders', 'o', 'o.`id_order` = hbd.`id_order`');
        $sql->innerJoin('customer', 'c', 'c.`id_customer` = hbd.`id_customer`');
        $sql->leftJoin('address', 'addr', 'addr.`id_address` = o.`id_address_delivery`');

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
     * Retorna as coordenadas geográficas (latitude e longitude) do hotel.
     *
     * @param int|null $idHotel ID do hotel (opcional)
     * @return array Coordenadas com latitude e longitude (com fallback RFC-004)
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
        if ($row && !empty((float) $row['latitude']) && !empty((float) $row['longitude'])) {
            return array(
                'latitude'  => (float) $row['latitude'],
                'longitude' => (float) $row['longitude'],
            );
        }

        return $defaultCoords;
    }

    /**
     * Gera o token de segurança para acesso exclusivo à tela do hóspede.
     * Utiliza chave criptográfica privada do sistema (_COOKIE_KEY_) para impedir IDOR.
     *
     * @param int $idOrder ID da reserva/pedido
     * @return string Token com 16 caracteres hexadecimais
     */
    public static function generateGuestToken($idOrder)
    {
        return substr(md5('qloarrival_' . (int) $idOrder . '_' . _COOKIE_KEY_), 0, 16);
    }

    /**
     * Valida de forma segura contra timing attacks o token de acesso do hóspede.
     *
     * @param int $idOrder ID do pedido
     * @param string $token Token informado na requisição
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
     * Obtém informações públicas e seguras de uma reserva para a tela do hóspede.
     * Mascara e restringe dados sensíveis para proteger a privacidade (LGPD).
     *
     * @param int $idOrder ID do pedido
     * @return array|false Dados resumidos da reserva ou false se inválida/cancelada
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

        // Anonimização / proteção de PII (Primeiro nome + inicial do sobrenome)
        $lastNameInitial = !empty($row['lastname']) ? mb_substr(trim($row['lastname']), 0, 1, 'UTF-8') . '.' : '';
        $row['guest_display_name'] = trim($row['firstname'] . ' ' . $lastNameInitial);

        return $row;
    }
}
