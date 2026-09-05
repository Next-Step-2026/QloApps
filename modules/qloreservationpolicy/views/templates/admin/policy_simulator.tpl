<div class="panel">
    <div class="panel-heading">
        <i class="icon-legal"></i> {l s='Simulador & Auditor de Políticas de Reserva' mod='qloreservationpolicy'}
    </div>

    {if $policyError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$policyError|escape:'html':'UTF-8'}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">{l s='Política a Avaliar:' mod='qloreservationpolicy'}</label>
            <div class="col-lg-5">
                <select name="policy_type" id="policy_type_select" class="form-control" onchange="togglePolicyFields(this.value)">
                    <option value="MINIMUM_STAY" {if $selectedPolicy == 'MINIMUM_STAY'}selected="selected"{/if}>
                        {l s='Estadia Mínima (Minimum Stay)' mod='qloreservationpolicy'}
                    </option>
                    <option value="ADVANCE_BOOKING" {if $selectedPolicy == 'ADVANCE_BOOKING'}selected="selected"{/if}>
                        {l s='Antecedência Mínima (Advance Booking)' mod='qloreservationpolicy'}
                    </option>
                    <option value="OVERBOOKING_LIMIT" {if $selectedPolicy == 'OVERBOOKING_LIMIT'}selected="selected"{/if}>
                        {l s='Limite de Overbooking Autorizado' mod='qloreservationpolicy'}
                    </option>
                </select>
            </div>
        </div>

        <!-- Campos: Estadia Mínima -->
        <div id="fields_min_stay" class="policy-field-group">
            <div class="form-group">
                <label class="control-label col-lg-3">{l s='Noites Solicitadas / Mínimo:' mod='qloreservationpolicy'}</label>
                <div class="col-lg-2">
                    <input type="number" name="requested_nights" value="{$currentValues.requested_nights|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Solicitadas' mod='qloreservationpolicy'}" />
                </div>
                <div class="col-lg-2">
                    <input type="number" name="required_minimum_nights" value="{$currentValues.required_minimum_nights|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Mínimo Exigido' mod='qloreservationpolicy'}" />
                </div>
            </div>
            <div class="form-group">
                <label class="control-label col-lg-3">{l s='Tipo de Quarto:' mod='qloreservationpolicy'}</label>
                <div class="col-lg-3">
                    <input type="text" name="room_type" value="{$currentValues.room_type|escape:'html':'UTF-8'}" class="form-control" placeholder="{l s='ex: standard, deluxe' mod='qloreservationpolicy'}" />
                </div>
            </div>
        </div>

        <!-- Campos: Antecedência Mínima -->
        <div id="fields_advance_booking" class="policy-field-group" style="display:none;">
            <div class="form-group">
                <label class="control-label col-lg-3">{l s='Dias de Antecedência:' mod='qloreservationpolicy'}</label>
                <div class="col-lg-2">
                    <input type="number" name="days_in_advance" value="{$currentValues.days_in_advance|escape:'html':'UTF-8'}" min="0" class="form-control" placeholder="{l s='Dias de Antecedência' mod='qloreservationpolicy'}" />
                </div>
                <div class="col-lg-2">
                    <input type="number" name="min_advance_days" value="{$currentValues.min_advance_days|escape:'html':'UTF-8'}" min="0" class="form-control" placeholder="{l s='Mínimo Exigido' mod='qloreservationpolicy'}" />
                </div>
            </div>
        </div>

        <!-- Campos: Limite de Overbooking -->
        <div id="fields_overbooking" class="policy-field-group" style="display:none;">
            <div class="form-group">
                <label class="control-label col-lg-3">{l s='Capacidade / Ocupação Atual:' mod='qloreservationpolicy'}</label>
                <div class="col-lg-2">
                    <input type="number" name="total_capacity" value="{$currentValues.total_capacity|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Capacidade Total' mod='qloreservationpolicy'}" />
                </div>
                <div class="col-lg-2">
                    <input type="number" name="current_occupied" value="{$currentValues.current_occupied|escape:'html':'UTF-8'}" min="0" class="form-control" placeholder="{l s='Ocupação Atual' mod='qloreservationpolicy'}" />
                </div>
            </div>
            <div class="form-group">
                <label class="control-label col-lg-3">{l s='Quartos Solicitados / Taxa Máx Overbooking:' mod='qloreservationpolicy'}</label>
                <div class="col-lg-2">
                    <input type="number" name="requested_units" value="{$currentValues.requested_units|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Solicitados' mod='qloreservationpolicy'}" />
                </div>
                <div class="col-lg-2">
                    <input type="number" step="0.01" name="max_overbooking_rate" value="{$currentValues.max_overbooking_rate|escape:'html':'UTF-8'}" min="0" max="1" class="form-control" placeholder="{l s='ex: 0.05 (5%)' mod='qloreservationpolicy'}" />
                </div>
            </div>
        </div>

        <div class="form-group">
            <div class="col-lg-offset-3 col-lg-4">
                <button type="submit" name="submitPolicySimulation" class="btn btn-primary btn-block">
                    <i class="icon-check"></i> {l s='Avaliar Regra' mod='qloreservationpolicy'}
                </button>
            </div>
        </div>
    </form>

    {if $policyEvaluation}
        <hr />
        <div class="well">
            <h4><i class="icon-certificate"></i> {l s='Resultado da Avaliação:' mod='qloreservationpolicy'}</h4>
            <p>
                <strong>{l s='Veredito:' mod='qloreservationpolicy'}</strong> 
                {if $policyEvaluation.decision == 'ALLOW'}
                    <span class="label label-success" style="font-size: 13px; padding: 4px 8px;">
                        <i class="icon-check"></i> {l s='AUTORIZADO (ALLOW)' mod='qloreservationpolicy'}
                    </span>
                {else}
                    <span class="label label-danger" style="font-size: 13px; padding: 4px 8px;">
                        <i class="icon-remove"></i> {l s='NEGADO (DENY)' mod='qloreservationpolicy'}
                    </span>
                {/if}
            </p>
            <p><strong>{l s='Código de Motivo:' mod='qloreservationpolicy'}</strong> <code>{$policyEvaluation.reason_code|escape:'html':'UTF-8'}</code></p>
            <p><strong>{l s='Justificativa:' mod='qloreservationpolicy'}</strong> {$policyEvaluation.explanation|escape:'html':'UTF-8'}</p>
            {if isset($policyEvaluation.correlation_id)}
                <p><small class="text-muted">{l s='Correlation ID:' mod='qloreservationpolicy'} {$policyEvaluation.correlation_id|escape:'html':'UTF-8'}</small></p>
            {/if}
        </div>
    {/if}
</div>

<script type="text/javascript">
function togglePolicyFields(selectedPolicy) {
    var minStayGroup = document.getElementById('fields_min_stay');
    var advBookingGroup = document.getElementById('fields_advance_booking');
    var overbookingGroup = document.getElementById('fields_overbooking');

    if (minStayGroup) minStayGroup.style.display = 'none';
    if (advBookingGroup) advBookingGroup.style.display = 'none';
    if (overbookingGroup) overbookingGroup.style.display = 'none';

    if (selectedPolicy === 'MINIMUM_STAY' && minStayGroup) {
        minStayGroup.style.display = 'block';
    } else if (selectedPolicy === 'ADVANCE_BOOKING' && advBookingGroup) {
        advBookingGroup.style.display = 'block';
    } else if (selectedPolicy === 'OVERBOOKING_LIMIT' && overbookingGroup) {
        overbookingGroup.style.display = 'block';
    }
}

document.addEventListener('DOMContentLoaded', function() {
    var select = document.getElementById('policy_type_select');
    if (select) {
        togglePolicyFields(select.value);
    }
});
</script>
