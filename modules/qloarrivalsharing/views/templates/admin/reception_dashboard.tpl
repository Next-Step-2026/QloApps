{if !$locationServiceOnline}
    <div class="alert alert-warning">
        <i class="icon-warning-sign"></i> 
        <strong>{l s='Aviso de Contingência:' mod='qloarrivalsharing'}</strong> 
        {l s='O serviço de cálculo de geofencing está temporariamente offline. A lista de reservas e traslados continua operacional.' mod='qloarrivalsharing'}
    </div>
{/if}

<div class="row">
    <div class="col-lg-3 col-md-6">
        <div class="panel">
            <div class="panel-body text-center">
                <i class="icon-calendar text-primary" style="font-size: 24px;"></i>
                <h4 class="text-muted">{l s='Data de Hoje' mod='qloarrivalsharing'}</h4>
                <h3><strong>{$currentDate|escape:'html':'UTF-8'}</strong></h3>
            </div>
        </div>
    </div>
    <div class="col-lg-3 col-md-6">
        <div class="panel">
            <div class="panel-body text-center">
                <i class="icon-car text-info" style="font-size: 24px;"></i>
                <h4 class="text-muted">{l s='Chegadas Previstas' mod='qloarrivalsharing'}</h4>
                <h3><strong>{$totalArrivals|escape:'html':'UTF-8'}</strong></h3>
            </div>
        </div>
    </div>
    <div class="col-lg-3 col-md-6">
        <div class="panel">
            <div class="panel-body text-center">
                <i class="icon-users text-success" style="font-size: 24px;"></i>
                <h4 class="text-muted">{l s='Total de Hóspedes' mod='qloarrivalsharing'}</h4>
                <h3><strong>{$totalGuests|escape:'html':'UTF-8'}</strong></h3>
            </div>
        </div>
    </div>
    <div class="col-lg-3 col-md-6">
        <div class="panel">
            <div class="panel-body text-center">
                <i class="icon-map-marker text-warning" style="font-size: 24px;"></i>
                <h4 class="text-muted">{l s='Geofence' mod='qloarrivalsharing'}</h4>
                <h3>
                    <strong>{$geofenceRadius|escape:'html':'UTF-8'} m</strong>
                    <a href="#" data-toggle="modal" data-target="#modalGeofenceConfig" class="text-muted" title="{l s='Alterar' mod='qloarrivalsharing'}" style="font-size: 14px; margin-left: 6px;">
                        <i class="icon-pencil"></i>
                    </a>
                </h3>
            </div>
        </div>
    </div>
</div>

<!-- Painel de Simulação de Envio de Coordenadas (Demonstração Rápida - RFC-004 Dia 9) -->
<div class="panel" id="panelSimulationCoordinates">
    <div class="panel-heading">
        <i class="icon-location-arrow text-primary"></i> 
        {l s='Simulação de Envio de Coordenadas (Demonstração Rápida)' mod='qloarrivalsharing'}
        <span class="text-muted" style="font-weight: normal; font-size: 12px; margin-left: 10px;">
            <i class="icon-building"></i> {l s='Hotel Ref:' mod='qloarrivalsharing'} <strong>{$hotelLat|string_format:"%.6f"}, {$hotelLng|string_format:"%.6f"}</strong> | {l s='Raio:' mod='qloarrivalsharing'} <strong>{$geofenceRadius|escape:'html':'UTF-8'}m</strong>
        </span>
        {if $arrivalResult}
            <span class="label {if $arrivalResult.alert_triggered}label-success{else}label-default{/if}" style="font-size: 11px; margin-left: 10px;">
                <i class="icon-check"></i> {$arrivalResult.distance_meters|string_format:"%.1f"}m ({$arrivalResult.transition|escape:'html':'UTF-8'})
            </span>
        {/if}
        <span class="panel-heading-action pull-right">
            <button type="button" id="btnToggleSimulation" class="btn btn-default btn-xs" data-toggle="collapse" data-target="#simulationCollapse" title="{l s='Expandir / Recolher Simulador' mod='qloarrivalsharing'}">
                <i class="icon-chevron-{if $arrivalResult || $arrivalError}up{else}down{/if}"></i> {l s='Simulador' mod='qloarrivalsharing'}
            </button>
        </span>
    </div>

    <div id="simulationCollapse" class="panel-collapse collapse {if $arrivalResult || $arrivalError}in{/if}">
        <div class="panel-body" style="padding: 15px 20px;">
            
            {if $arrivalError}
                <div class="alert alert-danger" style="margin-bottom: 20px;">
                    <i class="icon-exclamation-sign"></i> 
                    <strong>{l s='Falha no Teste:' mod='qloarrivalsharing'}</strong> {$arrivalError|escape:'html':'UTF-8'}
                </div>
            {/if}

            {if $arrivalResult}
                <div class="panel {if $arrivalResult.alert_triggered}panel-success{else}panel-default{/if}" style="border: 1px solid {if $arrivalResult.alert_triggered}#72c279{else}#d3d8db{/if}; margin-bottom: 20px; background-color: {if $arrivalResult.alert_triggered}#f4faf5{else}#fafafa{/if};">
                    <div class="panel-body" style="padding: 15px;">
                        <div class="row">
                            <div class="col-md-3 text-center" style="border-right: 1px solid #e0e0e0;">
                                <div class="text-muted" style="font-size: 11px; text-transform: uppercase; font-weight: bold;">{l s='Distância Calculada' mod='qloarrivalsharing'}</div>
                                <h2 style="margin: 5px 0 0 0; color: #333;">
                                    <strong>{$arrivalResult.distance_meters|string_format:"%.1f"|escape:'html':'UTF-8'}</strong> <small>m</small>
                                </h2>
                                <span class="text-muted" style="font-size: 12px;">{l s='Raio Configurado:' mod='qloarrivalsharing'} {$arrivalResult.geofence_radius_m|escape:'html':'UTF-8'}m</span>
                            </div>
                            <div class="col-md-3 text-center" style="border-right: 1px solid #e0e0e0;">
                                <div class="text-muted" style="font-size: 11px; text-transform: uppercase; font-weight: bold;">{l s='Transição Detectada' mod='qloarrivalsharing'}</div>
                                <div style="margin-top: 8px;">
                                    {if $arrivalResult.transition == 'ENTERED'}
                                        <span class="label label-success" style="font-size: 13px; padding: 4px 10px;">
                                            <i class="icon-sign-in"></i> ENTERED
                                        </span>
                                    {elseif $arrivalResult.transition == 'EXITED'}
                                        <span class="label label-warning" style="font-size: 13px; padding: 4px 10px;">
                                            <i class="icon-sign-out"></i> EXITED
                                        </span>
                                    {else}
                                        <span class="label label-default" style="font-size: 13px; padding: 4px 10px;">
                                            <i class="icon-minus"></i> NO_CHANGE
                                        </span>
                                    {/if}
                                </div>
                                <div class="text-muted" style="font-size: 12px; margin-top: 5px;">
                                    {$arrivalResult.previous_state|escape:'html':'UTF-8'} &rarr; <strong>{$arrivalResult.current_state|escape:'html':'UTF-8'}</strong>
                                </div>
                            </div>
                            <div class="col-md-3 text-center" style="border-right: 1px solid #e0e0e0;">
                                <div class="text-muted" style="font-size: 11px; text-transform: uppercase; font-weight: bold;">{l s='Alerta da Recepção' mod='qloarrivalsharing'}</div>
                                <div style="margin-top: 8px;">
                                    {if $arrivalResult.alert_triggered}
                                        <span class="label label-success" style="font-size: 13px; padding: 4px 10px;">
                                            <i class="icon-bell"></i> {l s='Disparado' mod='qloarrivalsharing'}
                                        </span>
                                    {else}
                                        <span class="label label-default" style="font-size: 13px; padding: 4px 10px;">
                                            <i class="icon-bell-slash"></i> {l s='Sem Disparo' mod='qloarrivalsharing'}
                                        </span>
                                    {/if}
                                </div>
                                <div class="text-muted" style="font-size: 12px; margin-top: 5px;">
                                    {if $arrivalResult.alert_triggered}
                                        {l s='Equipe de boas-vindas acionada' mod='qloarrivalsharing'}
                                    {else}
                                        {l s='Monitoramento contínuo' mod='qloarrivalsharing'}
                                    {/if}
                                </div>
                            </div>
                            <div class="col-md-3">
                                <div class="text-muted" style="font-size: 11px; text-transform: uppercase; font-weight: bold;">{l s='Diagnóstico do Motor Ktor' mod='qloarrivalsharing'}</div>
                                <p style="margin-top: 8px; font-size: 12px; line-height: 1.4; color: #555;">
                                    <i class="icon-info-circle text-info"></i> {$arrivalResult.message|escape:'html':'UTF-8'}
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            {/if}

            <div style="margin-bottom: 20px; background: #fafbfc; padding: 12px 15px; border-radius: 4px; border: 1px dashed #ced4da;">
                <div style="font-size: 12px; color: #444; font-weight: bold; margin-bottom: 8px;">
                    <i class="icon-magic text-primary"></i> {l s='Cenários de Demonstração Rápida (1 Clique - RFC-004):' mod='qloarrivalsharing'}
                </div>
                <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                    <button type="button" class="btn btn-default btn-sm" onclick="applySimulationPreset(-8.053100, -34.886100, 'outside', {$geofenceRadius|escape:'javascript'});" style="border-left: 4px solid #72c279;">
                        <i class="icon-check text-success"></i> 
                        <strong>1. {l s='Hóspede Chegando' mod='qloarrivalsharing'}</strong> 
                        <span class="text-muted">(~108m &bull; outside &bull; ENTERED)</span>
                    </button>
                    <button type="button" class="btn btn-default btn-sm" onclick="applySimulationPreset(-8.065000, -34.890000, 'outside', {$geofenceRadius|escape:'javascript'});" style="border-left: 4px solid #999;">
                        <i class="icon-road text-muted"></i> 
                        <strong>2. {l s='Hóspede Longe' mod='qloarrivalsharing'}</strong> 
                        <span class="text-muted">(~1.500m &bull; outside &bull; NO_CHANGE)</span>
                    </button>
                    <button type="button" class="btn btn-default btn-sm" onclick="applySimulationPreset(-8.055000, -34.888000, 'inside', {$geofenceRadius|escape:'javascript'});" style="border-left: 4px solid #f0ad4e;">
                        <i class="icon-sign-out text-warning"></i> 
                        <strong>3. {l s='Hóspede Saindo' mod='qloarrivalsharing'}</strong> 
                        <span class="text-muted">(~800m &bull; inside &bull; EXITED)</span>
                    </button>
                </div>
            </div>

            <form method="post" action="" id="formSimulationCoordinates">
                <input type="hidden" name="hotel_lat" value="{$hotelLat|escape:'html':'UTF-8'}" />
                <input type="hidden" name="hotel_lng" value="{$hotelLng|escape:'html':'UTF-8'}" />
                <input type="hidden" name="hotel_id" value="htl-prime-01" />
                <input type="hidden" name="id_hotel" value="{$idHotel|escape:'html':'UTF-8'}" />

                <div class="row">
                    <div class="col-md-3">
                        <div class="form-group">
                            <label class="control-label" for="sim_guest_lat">
                                <i class="icon-map-marker text-danger"></i> {l s='Latitude do Hóspede:' mod='qloarrivalsharing'}
                            </label>
                            <input type="number" step="any" name="guest_lat" id="sim_guest_lat" class="form-control" value="{$simGuestLat|escape:'html':'UTF-8'}" placeholder="-8.053100" required />
                        </div>
                    </div>
                    <div class="col-md-3">
                        <div class="form-group">
                            <label class="control-label" for="sim_guest_lng">
                                <i class="icon-map-marker text-danger"></i> {l s='Longitude do Hóspede:' mod='qloarrivalsharing'}
                            </label>
                            <input type="number" step="any" name="guest_lng" id="sim_guest_lng" class="form-control" value="{$simGuestLng|escape:'html':'UTF-8'}" placeholder="-34.886100" required />
                        </div>
                    </div>
                    <div class="col-md-3">
                        <div class="form-group">
                            <label class="control-label" for="sim_previous_state">
                                <i class="icon-history text-muted"></i> {l s='Estado Anterior:' mod='qloarrivalsharing'}
                            </label>
                            <select name="previous_state" id="sim_previous_state" class="form-control">
                                <option value="outside" {if $simPrevState == 'outside'}selected="selected"{/if}>{l s='outside (Fora do Raio - Padrão)' mod='qloarrivalsharing'}</option>
                                <option value="inside" {if $simPrevState == 'inside'}selected="selected"{/if}>{l s='inside (Já estava Dentro)' mod='qloarrivalsharing'}</option>
                            </select>
                        </div>
                    </div>
                    <div class="col-md-3">
                        <div class="form-group">
                            <label class="control-label" for="sim_radius">
                                <i class="icon-circle-o text-info"></i> {l s='Raio Geofence (m):' mod='qloarrivalsharing'}
                            </label>
                            <input type="number" step="1" min="1" name="radius" id="sim_radius" class="form-control" value="{$simRadius|escape:'html':'UTF-8'}" required />
                        </div>
                    </div>
                </div>

                <div class="row" style="margin-top: 10px;">
                    <div class="col-md-7">
                        <p class="text-muted" style="margin-top: 6px; font-size: 12px;">
                            <i class="icon-info-circle"></i> {l s='Envia requisição HTTP POST para o microserviço Kotlin com timeout estrito de 600ms.' mod='qloarrivalsharing'}
                        </p>
                    </div>
                    <div class="col-md-5 text-right">
                        <button type="submit" name="submitCheckLocation" class="btn btn-primary">
                            <i class="icon-play"></i> {l s='Disparar Verificação de Geofencing' mod='qloarrivalsharing'}
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </div>
</div>

<script type="text/javascript">
function applySimulationPreset(lat, lng, state, radius) {
    var inputLat = document.getElementById('sim_guest_lat');
    var inputLng = document.getElementById('sim_guest_lng');
    var selectState = document.getElementById('sim_previous_state');
    var inputRadius = document.getElementById('sim_radius');

    if (inputLat) inputLat.value = lat;
    if (inputLng) inputLng.value = lng;
    if (selectState) selectState.value = state;
    if (inputRadius && radius) inputRadius.value = radius;

    if (inputLat) {
        inputLat.focus();
    }
}

$(document).ready(function() {
    $('#simulationCollapse').on('show.bs.collapse', function () {
        $('#btnToggleSimulation i').removeClass('icon-chevron-down').addClass('icon-chevron-up');
    });
    $('#simulationCollapse').on('hide.bs.collapse', function () {
        $('#btnToggleSimulation i').removeClass('icon-chevron-up').addClass('icon-chevron-down');
    });
});
</script>

<div class="panel">
    <div class="panel-heading">
        <i class="icon-list"></i> {l s='Monitoramento de Chegadas e Traslados' mod='qloarrivalsharing'}
        <span class="badge">{$totalArrivals|escape:'html':'UTF-8'}</span>
        <span class="panel-heading-action pull-right">
            <a href="javascript:location.reload();" class="list-toolbar-btn" title="{l s='Atualizar' mod='qloarrivalsharing'}">
                <i class="process-icon-refresh"></i>
            </a>
        </span>
    </div>
    <div class="table-responsive">
        <table class="table table-striped table-hover">
            <thead>
                <tr>
                    <th class="text-center">{l s='Reserva' mod='qloarrivalsharing'}</th>
                    <th>{l s='Hóspede' mod='qloarrivalsharing'}</th>
                    <th>{l s='Contato' mod='qloarrivalsharing'}</th>
                    <th>{l s='Quarto' mod='qloarrivalsharing'}</th>
                    <th class="text-center">{l s='Ocupantes' mod='qloarrivalsharing'}</th>
                    <th class="text-center">{l s='Horário Previsto' mod='qloarrivalsharing'}</th>
                    <th class="text-center">{l s='Status do Traslado' mod='qloarrivalsharing'}</th>
                    <th class="text-right">{l s='Ações' mod='qloarrivalsharing'}</th>
                </tr>
            </thead>
            <tbody>
                {foreach from=$arrivals item=arrival}
                    <tr>
                        <td class="text-center">
                            <a href="{$orderAdminLink|escape:'html':'UTF-8'}&id_order={$arrival.id_order|escape:'html':'UTF-8'}&vieworder" target="_blank" class="btn btn-xs btn-default">
                                <i class="icon-search"></i> #{$arrival.id_order|escape:'html':'UTF-8'} ({$arrival.order_reference|escape:'html':'UTF-8'})
                            </a>
                        </td>
                        <td>
                            <strong>{$arrival.customer_name|escape:'html':'UTF-8'}</strong><br />
                            <small class="text-muted"><i class="icon-envelope"></i> {$arrival.customer_email|escape:'html':'UTF-8'}</small>
                        </td>
                        <td><i class="icon-phone"></i> {$arrival.customer_phone|escape:'html':'UTF-8'}</td>
                        <td>
                            {if !empty($arrival.room_num)}
                                <span class="label label-info">{l s='Quarto' mod='qloarrivalsharing'} {$arrival.room_num|escape:'html':'UTF-8'}</span>
                            {else}
                                <span class="label label-default">{l s='Não Atribuído' mod='qloarrivalsharing'}</span>
                            {/if}
                            <br />
                            <small class="text-muted">{$arrival.room_type_name|escape:'html':'UTF-8'}</small>
                        </td>
                        <td class="text-center">
                            <span class="badge">{$arrival.total_guests|escape:'html':'UTF-8'}</span>
                        </td>
                        <td class="text-center">
                            {if !empty($arrival.check_in_time)}
                                <i class="icon-time"></i> {$arrival.check_in_time|escape:'html':'UTF-8'}
                            {else}
                                <span class="text-muted">14:00</span>
                            {/if}
                        </td>
                        <td class="text-center">
                            <span class="label label-default">
                                <i class="icon-clock-o"></i> {l s='Aguardando Sinal' mod='qloarrivalsharing'}
                            </span>
                        </td>
                        <td class="text-right">
                            {if !empty($arrival.guest_link)}
                                <a href="{$arrival.guest_link|escape:'html':'UTF-8'}" target="_blank" class="btn btn-info btn-xs" title="{l s='Abrir Link do Hóspede' mod='qloarrivalsharing'}">
                                    <i class="icon-external-link"></i> {l s='Link Hóspede' mod='qloarrivalsharing'}
                                </a>
                            {/if}
                        </td>
                    </tr>
                {foreachelse}
                    <tr>
                        <td colspan="8" class="text-center" style="padding: 40px 15px;">
                            <i class="icon-calendar-check-o text-muted" style="font-size: 36px;"></i>
                            <h4 class="text-muted">{l s='Nenhuma chegada de hóspede prevista para a data de hoje.' mod='qloarrivalsharing'}</h4>
                        </td>
                    </tr>
                {/foreach}
            </tbody>
        </table>
    </div>
</div>

<div class="modal fade" id="modalGeofenceConfig" tabindex="-1" role="dialog" aria-hidden="true">
    <div class="modal-dialog modal-sm">
        <form method="post" action="">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal">&times;</button>
                    <h4 class="modal-title"><i class="icon-map-marker"></i> {l s='Configurar Raio de Geofence' mod='qloarrivalsharing'}</h4>
                </div>
                <div class="modal-body">
                    <div class="form-group">
                        <label class="control-label">{l s='Raio do Perímetro (metros):' mod='qloarrivalsharing'}</label>
                        <input type="number" name="geofence_radius" class="form-control" value="{$geofenceRadius|escape:'html':'UTF-8'}" min="1" step="1" required />
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal">{l s='Cancelar' mod='qloarrivalsharing'}</button>
                    <button type="submit" name="submitGeofenceRadius" class="btn btn-primary">{l s='Salvar' mod='qloarrivalsharing'}</button>
                </div>
            </div>
        </form>
    </div>
</div>
