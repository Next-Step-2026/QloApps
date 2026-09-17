# RFC-004 — Geofencing e Detecção de Chegada para Traslado

## 1. Identificação e Informações do Projeto

- **Código da Feature:** `QLO-FEAT-004`
- **Nome da Feature:** Geofencing e Detecção de Proximidade para Traslado e Recepção
- **Engenharia Responsável:** Engenharia de Sistemas de Localização, Geofencing & Backend
- **Stack do Desenvolvedor:** Kotlin / Java (Kotlin 1.9+, Ktor/Javalin / cálculos geodésicos e transições)
- **Stack de Apresentação:** Módulo QloApps (`qloarrivalsharing`, PHP 8.1+ / Smarty 3.x / HTML / CSS / JavaScript)
- **Porta de Execução Local:** `http://127.0.0.1:8104`
- **Prazo de Execução:** Milestone de 2 Semanas (10 dias úteis de sprint focado)

---

## 2. Contexto de Negócio e Motivação Operacional

Em propriedades hoteleiras que oferecem serviços de traslado (aeroporto/hotel) ou atendimento VIP, a recepção precisa saber com precisão o momento em que o veículo do hóspede entra no perímetro do hotel. Quando essa comunicação depende de mensagens manuais de WhatsApp ou ligações telefônicas, ocorrem atrasos no desembarque de malas, quartos não finalizados a tempo e veículos retidos na entrada principal.

O **Motor de Geofencing e Detecção de Chegada** soluciona essa dor através de um cálculo geodésico local e determinístico (fórmula de Haversine). O hóspede ou motorista fornece uma leitura pontual de GPS via navegador web mediante consentimento explícito. O sistema calcula a distância precisa em metros até a propriedade, avalia se o ponto cruzou o raio configurado de geofence (ex.: 200 metros) e dispara um alerta visual de transição (`ENTERED`), permitindo que a equipe prepare o quarto e organize o desembarque na entrada.

---

## 3. Histórias de Usuário e Personas

### Personas

- **Bruno (Concierge / Motorista de Traslado):** Conduz hóspedes do aeroporto ao hotel e precisa que a equipe de recepção saiba automaticamente quando a van estiver a 200 metros do portão principal.
- **Juliana (Chefe de Recepção):** Monitora a chegada dos hóspedes VIP e precisa de alertas visuais automáticos de aproximação para disponibilizar a chave do quarto antes do cliente descer do carro.

### Histórias de Usuário

1. **Detecção de Entrada no Raio do Hotel:**
   - *Como* recepcionista do hotel,
   - *Quero* ver o status da reserva mudar de *"A Caminho"* para *"Chegou às Imediações"*,
   - *Para que* eu solicite ao mensageiro que aguarde o hóspede na calçada.
2. **Consentimento e Coleta Pontual:**
   - *Como* hóspede utilizando o traslado,
   - *Quero* autorizar explicitamente o envio pontual da minha localização ao clicar em um botão *"Estou Chegando"*,
   - *Para que* minha privacidade seja respeitada sem rastreamento contínuo em segundo plano.
3. **Configuração de Raio de Proximidade:**
   - *Como* chefe de recepção,
   - *Quero* configurar o raio da geofence do hotel (ex.: 100m, 200m ou 500m),
   - *Para que* o alerta se adapte à topologia urbana e ao trânsito da região.

---

## 4. Escopo Operacional e Limites da Entrega

### No Escopo (In-Scope para o MVP)

- Módulo QloApps (`qloarrivalsharing`) com painel operacional administrativo de "Recepção / Traslados".
- Tela no front-office com botão "Estou Chegando" que captura uma única leitura da Geolocation API do navegador sob consentimento.
- Serviço local em Kotlin escutando em `127.0.0.1:8104` na rota `POST /v1/location-events`.
- Algoritmo matemático de cálculo de distância geodésica em metros (Fórmula de Haversine).
- Máquina de estados de transição de geofence: `ENTERED` (quando a distância passa de fora para dentro do raio), `EXITED` (quando sai) e `NO_CHANGE`.
- Validação estrita de limites de coordenadas: latitude $[-90.0..90.0]$ e longitude $[-180.0..180.0]$.
- Atualização em tempo real do painel administrativo exibindo distância calculada e alerta de chegada.
- Resiliência com timeout de 600ms e tratamento caso o serviço Kotlin esteja inativo.

### Fora do Escopo (Out-of-Scope para Versão 2)

- Rastreamento contínuo e em tempo real em segundo plano (background GPS tracking).
- Integração com mapas pesados de terceiros (Google Maps SDK, Mapbox API com chaves pagas).
- Cálculo de rota curva a curva ou previsão de trânsito com inteligência artificial (ETA preditivo).

---

## 5. Mapa de Entidades, Ciclo de Vida e Estados

### 5.1. Mapeamento de Tabelas Relacionais do QloApps (MySQL)
O geofencing consome dados de localização física e reservas:
- `ps_htl_branch_info`: Coordenadas de latitude e longitude do hotel e dados de endereço.
- `ps_htl_booking_detail`: Verificação de reservas com data de check-in correspondente à data atual.

### Diagrama de Transição de Geofence

```text
  [Posição Informada pelo Cliente]
                 │
                 ▼
  [Cálculo de Distância de Haversine]
                 │
                 ├── (Distância <= Raio e Estado Anterior == "outside") ──► [Transição: ENTERED (Dispara Alerta)]
                 │
                 ├── (Distância > Raio e Estado Anterior == "inside")   ──► [Transição: EXITED (Saiu do Raio)]
                 │
                 └── (Estado Não Mudou)                                 ──► [Transição: NO_CHANGE]
```

---

## 6. Topologia de Comunicação e Fluxo de Dados Ponta a Ponta

```text
[Navegador do Hóspede / Motorista]
         │
         │ 1. Clica em "Estou Chegando" (captura GPS sob consentimento)
         ▼
[QloApps Módulo: qloarrivalsharing (PHP/Smarty)]
         │
         │ 2. Valida reserva do dia, busca coordenadas do hotel e monta payload
         ▼ (HTTP POST síncrono em loopback, timeout 600ms)
[Serviço Local Kotlin: http://127.0.0.1:8104/v1/location-events]
   ├── 3.1. Validador de Limites de Latitude e Longitude
   ├── 3.2. Calculador Geodésico de Haversine (Distância em Metros)
   └── 3.3. Máquina de Transição de Estados (previous_state vs current_state)
         │
         │ 3.4. Retorna JSON com Distância, Estado Atual e Transição
         ▼
[QloApps Módulo PHP]
         │
         │ 4. Atualiza painel da recepção com badge "Chegou às Imediações"
         ▼
[Navegador da Recepção]
```

### Estrutura de Diretórios Recomendada

```text
projeto/
├── qloarrivalsharing/                 # Módulo PHP para QloApps
│   ├── qloarrivalsharing.php          # Registro do módulo e menus
│   ├── config.xml                     # Metadados
│   ├── controllers/
│   │   ├── admin/
│   │   │   └── AdminArrivalSharingController.php # Painel da recepção
│   │   └── front/
│   │       └── arrivaltracking.php    # Tela do hóspede (consentimento e envio)
│   └── views/
│       └── templates/
│           ├── admin/
│           │   └── reception_dashboard.tpl # View administrativa
│           └── front/
│               └── guest_arrival.tpl       # View do hóspede
│
└── location-service-kotlin/           # Serviço Local Kotlin
    ├── build.gradle.kts               # Configuração Gradle (Ktor Server, JUnit 5)
    ├── src/
    │   ├── main/kotlin/com/hotel/location/
    │   │   ├── Application.kt         # Servidor Ktor e rotas HTTP
    │   │   ├── model/
    │   │   │   └── GeoModels.kt       # Data classes para coordenadas e eventos
    │   │   └── service/
    │   │       └── HaversineEngine.kt # Cálculo de Haversine e transições
    │   └── test/kotlin/com/hotel/location/
    │       └── HaversineEngineTest.kt # Testes unitários de precisão matemática
```

---

## 7. Regras de Negócio Detalhadas e Tabela de Casos de Borda

| ID | Regra de Negócio | Condição de Entrada | Comportamento Esperado | Caso de Borda / Tratamento |
| --- | --- | --- | --- | --- |
| **RN-001** | **Validação de Limites de Coordenadas** | Latitude fora de $[-90..90]$ ou Longitude fora de $[-180..180]$ | Rejeitar com HTTP `400 Bad Request` e código `INVALID_COORDINATES`. | Coordenadas nulas ou strings não numéricas rejeitadas no schema. |
| **RN-002** | **Cálculo de Distância de Haversine** | Par de coordenadas válidas: Hotel $(lat_1, lng_1)$ e Hóspede $(lat_2, lng_2)$ | Calcular distância esférica precisa em metros com raio médio da Terra $R = 6.371.000\text{ m}$. | Tratar distância zero (hóspede no ponto exato) retornando $0.0\text{ m}$. |
| **RN-003** | **Transição ENTERED** | `previous_state == "outside"` e `distance_meters <= geofence_radius_m` | Definir `current_state = "inside"`, `transition = "ENTERED"` e `alert_triggered = true`. | Disparar alerta visual na recepção. |
| **RN-004** | **Transição EXITED** | `previous_state == "inside"` e `distance_meters > geofence_radius_m` | Definir `current_state = "outside"`, `transition = "EXITED"` e `alert_triggered = false`. | Ocorre se o veículo ultrapassar o hotel e se afastar. |
| **RN-005** | **Manutenção de Estado (NO_CHANGE)** | Hóspede permanece fora da geofence ou permanece dentro | Definir `transition = "NO_CHANGE"`. | Não disparar novo alerta caso o hóspede já esteja dentro do raio. |
| **RN-006** | **Raio Mínimo de Geofence** | `geofence_radius_m` informado $\le 0$ | Rejeitar com HTTP `400` e assumir default de $200\text{ metros}$ no módulo PHP. | Evitar raio zero ou negativo. |

---

## 8. Especificação Completa do Contrato de API (OpenAPI / RFC 7807)

### Endpoint Local

- **URL:** `http://127.0.0.1:8104/v1/location-events`
- **Método:** `POST`
- **Headers Obrigatórios:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid-v4>`

### Schema de Entrada (Request Body)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["hotel_id", "hotel_lat", "hotel_lng", "guest_lat", "guest_lng", "geofence_radius_m", "previous_state"],
  "properties": {
    "hotel_id": { "type": "string" },
    "hotel_lat": { "type": "number", "minimum": -90.0, "maximum": 90.0 },
    "hotel_lng": { "type": "number", "minimum": -180.0, "maximum": 180.0 },
    "guest_lat": { "type": "number", "minimum": -90.0, "maximum": 90.0 },
    "guest_lng": { "type": "number", "minimum": -180.0, "maximum": 180.0 },
    "geofence_radius_m": { "type": "number", "minimum": 1.0 },
    "previous_state": { "type": "string", "enum": ["inside", "outside"] }
  }
}
```

### Exemplo de Requisição (Request)

```json
{
  "hotel_id": "htl-recife-01",
  "hotel_lat": -8.052240,
  "hotel_lng": -34.885650,
  "guest_lat": -8.053100,
  "guest_lng": -34.886100,
  "geofence_radius_m": 200.0,
  "previous_state": "outside"
}
```

### Exemplo de Resposta (Response: 200 OK)

```json
{
  "correlation_id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "hotel_id": "htl-recife-01",
  "distance_meters": 108.7,
  "current_state": "inside",
  "transition": "ENTERED",
  "alert_triggered": true,
  "message": "Hóspede entrou no raio de 200m da propriedade."
}
```

### Schema de Erro Estruturado (RFC 7807 — 400 Bad Request)

```json
{
  "type": "https://hotel.local/errors/invalid-coordinates",
  "title": "Coordenadas Geográficas Inválidas",
  "status": 400,
  "detail": "A latitude informada (95.0) ultrapassa os limites válidos de -90 a +90.",
  "instance": "/v1/location-events"
}
```

### Matriz de Códigos HTTP

| Código | Nome | Condição | Ação do QloApps |
| --- | --- | --- | --- |
| `200` | OK | Distância calculada e transição identificada. | Atualiza painel da recepção com distância e badge. |
| `400` | Bad Request | Coordenadas fora dos limites válidos. | Exibe mensagem de erro de coordenadas. |
| `503` | Service Unavailable | Serviço Kotlin offline na porta 8104. | Renderiza aviso de indisponibilidade de geofence. |

---

## 9. Massa de Dados de Teste e Cenários de Fixture

```json
[
  {
    "description": "Hóspede se aproximando a 108 metros do hotel (Transição ENTERED)",
    "input": {
      "hotel_id": "htl-01",
      "hotel_lat": -8.052240,
      "hotel_lng": -34.885650,
      "guest_lat": -8.053100,
      "guest_lng": -34.886100,
      "geofence_radius_m": 200.0,
      "previous_state": "outside"
    },
    "expected_output": {
      "distance_meters_approx": 108.7,
      "current_state": "inside",
      "transition": "ENTERED",
      "alert_triggered": true
    }
  },
  {
    "description": "Hóspede distante a 1.500 metros do hotel (Transição NO_CHANGE)",
    "input": {
      "hotel_id": "htl-01",
      "hotel_lat": -8.052240,
      "hotel_lng": -34.885650,
      "guest_lat": -8.065000,
      "guest_lng": -34.890000,
      "geofence_radius_m": 200.0,
      "previous_state": "outside"
    },
    "expected_output": {
      "current_state": "outside",
      "transition": "NO_CHANGE",
      "alert_triggered": false
    }
  },
  {
    "description": "Hóspede saindo do raio da geofence (Transição EXITED)",
    "input": {
      "hotel_id": "htl-01",
      "hotel_lat": -8.052240,
      "hotel_lng": -34.885650,
      "guest_lat": -8.055000,
      "guest_lng": -34.888000,
      "geofence_radius_m": 200.0,
      "previous_state": "inside"
    },
    "expected_output": {
      "current_state": "outside",
      "transition": "EXITED",
      "alert_triggered": false
    }
  }
]
```

---

## 10. Critérios de Aceitação em BDD (Gherkin: Given-When-Then)

```gherkin
Feature: Detecção de Chegada de Hóspedes via Geofencing

  Scenario: Entrada do hóspede no raio de 200 metros dispara alerta
    Given que o serviço Kotlin está ativo em "http://127.0.0.1:8104"
    And o hotel está em "(-8.052240, -34.885650)" com raio de 200m
    And o estado anterior do hóspede era "outside"
    When o hóspede reporta a posição "(-8.053100, -34.886100)"
    Then o status HTTP da resposta deve ser 200
    And o campo "current_state" deve ser "inside"
    And o campo "transition" deve ser "ENTERED"
    And o campo "alert_triggered" deve ser verdadeiro

  Scenario: Hóspede distante não aciona alerta
    Given que o serviço Kotlin está ativo
    And o estado anterior era "outside"
    When o hóspede envia coordenadas a 1.500 metros do hotel
    Then o status HTTP deve ser 200
    And o campo "current_state" deve ser "outside"
    And o campo "transition" deve ser "NO_CHANGE"
    And o campo "alert_triggered" deve ser falso

  Scenario: Rejeição de latitude com valor inválido
    Given que o serviço Kotlin está ativo
    When uma requisição é enviada com "guest_lat = 95.0"
    Then o status HTTP retornado deve ser 400
    And o corpo da resposta deve conter "INVALID_COORDINATES"

  Scenario: Contingência com serviço Kotlin inativo
    Given que o microserviço Kotlin na porta 8104 está parado
    When a recepção consulta a lista de traslados no QloApps
    Then o módulo PHP captura o timeout em no máximo 600ms
    And a tela exibe aviso de cálculo de proximidade temporariamente indisponível
```

---

## 11. Guia de Integração com o QloApps (Módulo PHP / Smarty)

### Classe Principal do Módulo (`qloarrivalsharing.php`)
```php
<?php
// modules/qloarrivalsharing/qloarrivalsharing.php

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloArrivalSharing extends Module
{
    public function __construct()
    {
        $this->name = 'qloarrivalsharing';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Chegada e Traslado de Hóspedes');
        $this->description = $this->l('Geofencing e cálculo de proximidade para recepção.');
    }

    public function install()
    {
        return parent::install() && $this->installTab();
    }

    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    private function installTab()
    {
        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminArrivalSharing';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Recepção / Traslado';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;
        return $tab->add();
    }

    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminArrivalSharing');
        if ($idTab) {
            $tab = new Tab($idTab);
            return $tab->delete();
        }
        return true;
    }
}
```

### Controller PHP (`AdminArrivalSharingController.php`)

```php
<?php
// modules/qloarrivalsharing/controllers/admin/AdminArrivalSharingController.php

class AdminArrivalSharingController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    public function initContent()
    {
        parent::initContent();

        $arrivalResult = null;
        $errorMessage  = null;

        if (Tools::isSubmit('submitCheckLocation')) {
            $hotelLat  = (float) Tools::getValue('hotel_lat', -8.052240);
            $hotelLng  = (float) Tools::getValue('hotel_lng', -34.885650);
            $guestLat  = (float) Tools::getValue('guest_lat');
            $guestLng  = (float) Tools::getValue('guest_lng');
            $prevState = Tools::getValue('previous_state', 'outside');
            $radius    = (float) Tools::getValue('radius', 200.0);
            $corrId    = Tools::passwdGen(16, 'ALPHANUMERIC');

            $payload = json_encode([
                'hotel_id'          => 'htl-recife-01',
                'hotel_lat'         => $hotelLat,
                'hotel_lng'         => $hotelLng,
                'guest_lat'         => $guestLat,
                'guest_lng'         => $guestLng,
                'geofence_radius_m' => $radius,
                'previous_state'    => $prevState
            ]);

            $ch = curl_init('http://127.0.0.1:8104/v1/location-events');
            curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
            curl_setopt($ch, CURLOPT_POST, true);
            curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
            curl_setopt($ch, CURLOPT_TIMEOUT_MS, 600);
            curl_setopt($ch, CURLOPT_HTTPHEADER, [
                'Content-Type: application/json',
                'X-Correlation-ID: ' . $corrId
            ]);

            $response = curl_exec($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            curl_close($ch);

            if ($response && $httpCode === 200) {
                $arrivalResult = json_decode($response, true);
            } else {
                $errorMessage = 'Serviço de cálculo de chegada offline (HTTP ' . $httpCode . ').';
            }
        }

        $this->context->smarty->assign([
            'arrivalResult' => $arrivalResult,
            'arrivalError'  => $errorMessage,
            'hotelLat'      => -8.052240,
            'hotelLng'      => -34.885650
        ]);

        $this->setTemplate('reception_dashboard.tpl');
    }
}
```

### Template Smarty (`reception_dashboard.tpl`)

```html
<div class="panel">
    <div class="panel-heading">
        <i class="icon-map-marker"></i> Painel de Recepção & Monitoramento de Traslados
    </div>

    {if $arrivalError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$arrivalError}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <input type="hidden" name="hotel_lat" value="{$hotelLat}" />
        <input type="hidden" name="hotel_lng" value="{$hotelLng}" />

        <div class="form-group">
            <label class="control-label col-lg-3">Simular Coordenadas do Hóspede:</label>
            <div class="col-lg-3">
                <input type="text" name="guest_lat" value="-8.053100" placeholder="Latitude" class="form-control" required />
            </div>
            <div class="col-lg-3">
                <input type="text" name="guest_lng" value="-34.886100" placeholder="Longitude" class="form-control" required />
            </div>
        </div>

        <div class="form-group">
            <label class="control-label col-lg-3">Raio de Geofence (metros):</label>
            <div class="col-lg-3">
                <input type="number" name="radius" value="200" class="form-control" required />
            </div>
            <div class="col-lg-3">
                <select name="previous_state" class="form-control">
                    <option value="outside">Estado Anterior: Fora (outside)</option>
                    <option value="inside">Estado Anterior: Dentro (inside)</option>
                </select>
            </div>
            <div class="col-lg-3">
                <button type="submit" name="submitCheckLocation" class="btn btn-primary btn-block">
                    <i class="icon-refresh"></i> Calcular Proximidade
                </button>
            </div>
        </div>
    </form>

    {if $arrivalResult}
        <hr />
        <div class="well">
            <h4><i class="icon-info-sign"></i> Resultado da Detecção:</h4>
            <p><strong>Distância da Propriedade:</strong> {$arrivalResult.distance_meters|string_format:"%.1f"} metros</p>
            <p><strong>Status Atual:</strong> 
                {if $arrivalResult.current_state == 'inside'}
                    <span class="label label-success">DENTRO DO RAIO</span>
                {else}
                    <span class="label label-default">FORA DO RAIO</span>
                {/if}
            </p>
            <p><strong>Transição Detectada:</strong> <span class="badge badge-info">{$arrivalResult.transition}</span></p>
            
            {if $arrivalResult.alert_triggered}
                <div class="alert alert-success">
                    <i class="icon-bell"></i> <strong>ALERTA DE CHEGADA:</strong> {$arrivalResult.message}
                </div>
            {/if}
        </div>
    {/if}
</div>
```

---

### 11.4. Código Inicial Standalone do Serviço Kotlin/Java (`Application.kt`)
```kotlin
// location-service-kotlin/src/main/kotlin/com/hotel/location/Application.kt
package com.hotel.location

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.*

@Serializable
data class LocationEventRequest(
    val hotel_id: String,
    val hotel_lat: Double,
    val hotel_lng: Double,
    val guest_lat: Double,
    val guest_lng: Double,
    val geofence_radius_m: Double,
    val previous_state: String
)

fun calculateHaversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun main() {
    embeddedServer(Netty, port = 8104, host = "127.0.0.1") {
        routing {
            get("/healthz") {
                call.respondText("{\"status\":\"UP\"}", ContentType.Application.Json)
            }
            post("/v1/location-events") {
                try {
                    val rawBody = call.receiveText()
                    val req = Json { ignoreUnknownKeys = true }.decodeFromString<LocationEventRequest>(rawBody)
                    
                    val distanceMeters = calculateHaversineMeters(req.hotel_lat, req.hotel_lng, req.guest_lat, req.guest_lng)
                    val currentState = if (distanceMeters <= req.geofence_radius_m) "inside" else "outside"
                    
                    val transition = when {
                        req.previous_state == "outside" && currentState == "inside" -> "ENTERED"
                        req.previous_state == "inside" && currentState == "outside" -> "EXITED"
                        else -> "NO_CHANGE"
                    }
                    val alertTriggered = (transition == "ENTERED")

                    val responseJson = """
                    {
                      "correlation_id": "${call.request.headers["X-Correlation-ID"] ?: "corr-demo"}",
                      "hotel_id": "${req.hotel_id}",
                      "distance_meters": ${"%.1f".format(java.util.Locale.US, distanceMeters)},
                      "current_state": "$currentState",
                      "transition": "$transition",
                      "alert_triggered": $alertTriggered,
                      "message": "${if (alertTriggered) "Hospede entrou no raio de proximidade do hotel." else "Posicao atualizada sem alerta."}"
                    }
                    """.trimIndent()
                    call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.respondText("{\"error\":\"MALFORMED_JSON\",\"message\":\"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }
        }
    }.start(wait = true)
}
```

## 12. Observabilidade, Logs Estruturados e SLAs Operacionais

### Níveis de Serviço (SLAs / SLOs)

- **Latência P95:** $< 10\text{ ms}$ para cálculo de Haversine em Kotlin.
- **Latência Total Ponta a Ponta (PHP + cURL + Kotlin):** $< 60\text{ ms}$.
- **Timeout Máximo do Cliente:** $600\text{ ms}$.

### Formato de Log Estruturado (Stdout do Serviço Kotlin)

```json
{
  "timestamp": "2026-08-27T10:30:12.441Z",
  "level": "INFO",
  "correlation_id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "event": "GEOFENCE_EVALUATED",
  "hotel_id": "htl-recife-01",
  "distance_meters": 108.7,
  "transition": "ENTERED",
  "duration_ms": 1.12
}
```

---

## 13. Matriz de Riscos, Segurança e Privacidade

| Ameaça Identificada | Impacto | Nível | Controle Técnico Aplicado |
| --- | --- | --- | --- |
| **Rastreamento Invasivo sem Consentimento** | Violação de privacidade e LGPD | Crítico | Coleta pontual acionada apenas quando o hóspede clica no botão com aviso explícito. |
| **Persistência de Trilha de GPS em Disco** | Vazamento de dados de localização | Alto | Logs gravam apenas `distance_meters` arredondado; coordenadas brutas não são salvas em log. |
| **Exposição da Porta na Rede Local** | Acesso não autenticado | Alto | Servidor Ktor configurado estritamente para escutar em `127.0.0.1:8104`. |

---

## 14. Guia de Diagnóstico e Resolução de Problemas (Troubleshooting FAQ)

### FAQ Técnico

1. **Erro: `cURL error 7: Failed to connect to 127.0.0.1 port 8104`**
   - *Causa:* O servidor Kotlin Ktor não está em execução.
   - *Solução:* No diretório do serviço Kotlin, execute `./gradlew run`.
2. **Distância calculada muito maior ou menor do que o esperado no mapa**
   - *Causa:* Inversão entre latitude e longitude nos parâmetros de entrada.
   - *Solução:* Certifique-se de que a latitude (eixo Y, $-90$ a $+90$) e longitude (eixo X, $-180$ a $+180$) estão na ordem correta.
3. **Transição `ENTERED` não disparada mesmo dentro do raio**
   - *Causa:* O `previous_state` foi enviado como `"inside"`, resultando na transição `"NO_CHANGE"`.
   - *Solução:* A transição `ENTERED` ocorre apenas quando o estado anterior era `"outside"`.

---

## 15. Plano de Execução Diário (Cronograma de 10 Dias Úteis)

### Semana 1: Serviço Kotlin e Motor de Geofencing (10h)

- **Dia 1 (2h):** Setup do projeto Kotlin com Gradle e Ktor HTTP Server.
- **Dia 2 (2h):** Criação das classes de dados de requisição e resposta com serialização JSON.
- **Dia 3 (2h):** Implementação da fórmula de Haversine e funções de validação de coordenadas.
- **Dia 4 (2h):** Implementação da lógica de transições de geofence (`ENTERED`, `EXITED`, `NO_CHANGE`).
- **Dia 5 (2h):** Criação da rota HTTP na porta 8104 e bateria de testes unitários com JUnit 5 cobrindo as fixtures.

### Semana 2: Módulo QloApps e Painel de Recepção (10h)

- **Dia 6 (2h):** Scaffolding do módulo `qloarrivalsharing` e registro do menu administrativo de traslados.
- **Dia 7 (2h):** Desenvolvimento da view Smarty com tabela de monitoramento de reservas do dia.
- **Dia 8 (2h):** Implementação do cliente cURL no PHP com timeout de 600ms e tratamento de erros.
- **Dia 9 (2h):** Desenvolvimento da tela de simulação de envio de coordenadas para demonstração rápida.
- **Dia 10 (2h):** Validação dos 4 cenários BDD, teste de queda de serviço e preparação do roteiro de apresentação.

---

### 15.1. Quickstart de 1 Linha (Execução & Teste cURL)
```bash
# 1. Iniciar o serviço Kotlin de geofencing:
./gradlew run

# 2. Em outro terminal, enviar coordenadas de teste via cURL:
curl -s -X POST http://127.0.0.1:8104/v1/location-events   -H "Content-Type: application/json"   -H "X-Correlation-ID: test-geo-01"   -d '{"hotel_id": "htl-01", "hotel_lat": -8.052240, "hotel_lng": -34.885650, "guest_lat": -8.053100, "guest_lng": -34.886100, "geofence_radius_m": 200.0, "previous_state": "outside"}' | jq .
```

## 16. Definição de Pronto (Definition of Done — DoD Checklist)

- [ ] Serviço Kotlin compila e responde com sucesso em `http://127.0.0.1:8104/healthz`.
- [ ] Testes unitários do cálculo de Haversine passam com 100% de sucesso.
- [ ] Módulo QloApps instala sem conflitos e adiciona o painel de traslados no back-office.
- [ ] Simulação de coordenadas atualiza o status de aproximação do hóspede em tempo real.
- [ ] QloApps exibe mensagem de contingência adequada caso o serviço Kotlin esteja desligado.
- [ ] Documentação de compilação e fixtures testadas com sucesso.