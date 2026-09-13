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
                            <a href="{$orderAdminLink|escape:'html':'UTF-8'}&id_order={$arrival.id_order|escape:'html':'UTF-8'}&vieworder" target="_blank" class="btn btn-default btn-xs">
                                <i class="icon-eye"></i> {l s='Detalhes' mod='qloarrivalsharing'}
                            </a>
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
