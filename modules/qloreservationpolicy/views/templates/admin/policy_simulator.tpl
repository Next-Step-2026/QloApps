<div class="panel">
    <div class="panel-heading">
        <i class="icon-legal"></i> {l s='Reservation Policy Simulator & Auditor' mod='qloreservationpolicy'}
    </div>

    {if $policyError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$policyError|escape:'html':'UTF-8'}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">{l s='Policy to Evaluate:' mod='qloreservationpolicy'}</label>
            <div class="col-lg-5">
                <select name="policy_type" id="policy_type_select" class="form-control" onchange="togglePolicyFields(this.value)">
                    <option value="MINIMUM_STAY" {if $selectedPolicy == 'MINIMUM_STAY'}selected="selected"{/if}>
                        {l s='Minimum Stay' mod='qloreservationpolicy'}
                    </option>
                    <option value="ADVANCE_BOOKING" {if $selectedPolicy == 'ADVANCE_BOOKING'}selected="selected"{/if}>
                        {l s='Advance Booking' mod='qloreservationpolicy'}
                    </option>
                    <option value="OVERBOOKING_LIMIT" {if $selectedPolicy == 'OVERBOOKING_LIMIT'}selected="selected"{/if}>
                        {l s='Authorized Overbooking Limit' mod='qloreservationpolicy'}
                    </option>
                </select>
            </div>
        </div>

        <!-- Campos: Estadia Mínima -->
        <div id="fields_min_stay" class="policy-field-group">
            <div class="form-group">
                <div class="col-lg-offset-3 col-lg-5">
                    <div class="row">
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">{l s='Requested Nights:' mod='qloreservationpolicy'}</label>
                            <input type="number" name="requested_nights" value="{$currentValues.requested_nights|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Requested' mod='qloreservationpolicy'}" />
                        </div>
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">{l s='Required Minimum:' mod='qloreservationpolicy'}</label>
                            <input type="number" name="required_minimum_nights" value="{$currentValues.required_minimum_nights|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Required Minimum' mod='qloreservationpolicy'}" />
                        </div>
                    </div>
                </div>
            </div>
            <div class="form-group">
                <label class="control-label col-lg-3">{l s='Room Type:' mod='qloreservationpolicy'}</label>
                <div class="col-lg-5">
                    <input type="text" name="room_type" value="{$currentValues.room_type|escape:'html':'UTF-8'}" class="form-control" placeholder="{l s='e.g. standard, deluxe' mod='qloreservationpolicy'}" />
                </div>
            </div>
        </div>

        <!-- Campos: Antecedência Mínima -->
        <div id="fields_advance_booking" class="policy-field-group" style="display:none;">
            <div class="form-group">
                <div class="col-lg-offset-3 col-lg-5">
                    <div class="row">
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">{l s='Days in Advance:' mod='qloreservationpolicy'}</label>
                            <input type="number" name="days_in_advance" value="{$currentValues.days_in_advance|escape:'html':'UTF-8'}" min="0" class="form-control" placeholder="{l s='Days in Advance' mod='qloreservationpolicy'}" />
                        </div>
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">{l s='Required Minimum:' mod='qloreservationpolicy'}</label>
                            <input type="number" name="min_advance_days" value="{$currentValues.min_advance_days|escape:'html':'UTF-8'}" min="0" class="form-control" placeholder="{l s='Required Minimum' mod='qloreservationpolicy'}" />
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Campos: Limite de Overbooking -->
        <div id="fields_overbooking" class="policy-field-group" style="display:none;">
            <div class="form-group">
                <div class="col-lg-offset-3 col-lg-5">
                    <div class="row">
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">{l s='Total Capacity:' mod='qloreservationpolicy'}</label>
                            <input type="number" name="total_capacity" value="{$currentValues.total_capacity|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Total Capacity' mod='qloreservationpolicy'}" />
                        </div>
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">
                                {l s='Current Occupancy:' mod='qloreservationpolicy'}
                                <span class="label-tooltip" data-toggle="tooltip" data-placement="top" data-html="true" data-original-title="{l s='Total confirmed room bookings, not physical guests. It may exceed physical capacity if the hotel is already operating in overbooking.' mod='qloreservationpolicy'}" style="cursor: pointer; color: #5bc0de; margin-left: 4px;">
                                    <i class="icon-question-sign"></i>
                                </span>
                            </label>
                            <input type="number" name="current_occupied" value="{$currentValues.current_occupied|escape:'html':'UTF-8'}" min="0" class="form-control" placeholder="{l s='Current Occupancy' mod='qloreservationpolicy'}" />
                        </div>
                    </div>
                </div>
            </div>
            <div class="form-group">
                <div class="col-lg-offset-3 col-lg-5">
                    <div class="row">
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">{l s='Requested Rooms:' mod='qloreservationpolicy'}</label>
                            <input type="number" name="requested_units" value="{$currentValues.requested_units|escape:'html':'UTF-8'}" min="1" class="form-control" placeholder="{l s='Requested' mod='qloreservationpolicy'}" />
                        </div>
                        <div class="col-xs-6">
                            <label class="control-label" style="text-align: left; padding-top: 0; margin-bottom: 5px; display: block;">{l s='Max Overbooking Rate:' mod='qloreservationpolicy'}</label>
                            <div class="input-group">
                                <input type="number" step="0.1" name="max_overbooking_rate" value="{$currentValues.max_overbooking_rate|escape:'html':'UTF-8'}" min="0" max="100" class="form-control" placeholder="5" />
                                <span class="input-group-addon">%</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="form-group">
            <div class="col-lg-offset-3 col-lg-4">
                <button type="submit" name="submitPolicySimulation" class="btn btn-primary btn-block">
                    <i class="icon-check"></i> {l s='Evaluate Policy' mod='qloreservationpolicy'}
                </button>
            </div>
        </div>
    </form>

    {if $policyEvaluation}
        <hr />
        <div class="well">
            <h4><i class="icon-certificate"></i> {l s='Evaluation Result:' mod='qloreservationpolicy'}</h4>
            <p>
                <strong>{l s='Verdict:' mod='qloreservationpolicy'}</strong> 
                {if $policyEvaluation.decision == 'ALLOW'}
                    <span class="label label-success" style="font-size: 13px; padding: 4px 8px;">
                        <i class="icon-check"></i> {l s='AUTHORIZED (ALLOW)' mod='qloreservationpolicy'}
                    </span>
                {else}
                    <span class="label label-danger" style="font-size: 13px; padding: 4px 8px;">
                        <i class="icon-remove"></i> {l s='DENIED (DENY)' mod='qloreservationpolicy'}
                    </span>
                {/if}
            </p>
            <p><strong>{l s='Reason Code:' mod='qloreservationpolicy'}</strong> <code>{$policyEvaluation.reason_code|escape:'html':'UTF-8'}</code></p>
            <p><strong>{l s='Explanation:' mod='qloreservationpolicy'}</strong> {$policyEvaluation.explanation|escape:'html':'UTF-8'}</p>
            {if isset($policyEvaluation.correlation_id)}
                <p><small class="text-muted">{l s='Correlation ID:' mod='qloreservationpolicy'} {$policyEvaluation.correlation_id|escape:'html':'UTF-8'}</small></p>
            {/if}
        </div>
    {/if}
</div>

<div class="panel">
    <div class="panel-heading">
        <i class="icon-list-alt"></i> {l s='Policy Audit Log (JSON)' mod='qloreservationpolicy'}
        <span class="badge">{$totalAuditLogs|intval}</span>
        <span class="panel-heading-action pull-right">
            <form method="post" action="" style="display: inline-block; margin: 0;">
                <button type="submit" name="downloadAuditLog" class="btn btn-default btn-xs" {if $totalAuditLogs == 0}disabled="disabled"{/if}>
                    <i class="icon-download"></i> {l s='Download JSON' mod='qloreservationpolicy'}
                </button>
                <button type="submit" name="clearAuditLog" class="btn btn-default btn-xs" {if $totalAuditLogs == 0}disabled="disabled"{/if} onclick="return confirm('{l s='Are you sure you want to clear the audit log?' mod='qloreservationpolicy'}');">
                    <i class="icon-trash"></i> {l s='Clear Log' mod='qloreservationpolicy'}
                </button>
            </form>
        </span>
    </div>
    <div class="panel-body" style="padding: 0;">
        {if $auditLogs && count($auditLogs) > 0}
            <div class="table-responsive">
                <table class="table table-striped table-hover" style="margin-bottom: 0;">
                    <thead>
                        <tr>
                            <th style="width: 170px;">{l s='Timestamp' mod='qloreservationpolicy'}</th>
                            <th style="width: 160px;">{l s='Correlation ID' mod='qloreservationpolicy'}</th>
                            <th style="width: 160px;">{l s='Policy' mod='qloreservationpolicy'}</th>
                            <th style="width: 100px; text-align: center;">{l s='Verdict' mod='qloreservationpolicy'}</th>
                            <th>{l s='Reason & Explanation' mod='qloreservationpolicy'}</th>
                            <th style="width: 250px;">{l s='Facts Evaluated' mod='qloreservationpolicy'}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {foreach from=$auditLogs item=log}
                            <tr>
                                <td><code>{$log.timestamp|escape:'html':'UTF-8'}</code></td>
                                <td><small class="text-muted">{$log.correlation_id|escape:'html':'UTF-8'}</small></td>
                                <td><span class="label label-info">{$log.policy|escape:'html':'UTF-8'}</span></td>
                                <td style="text-align: center;">
                                    {if $log.decision == 'ALLOW'}
                                        <span class="label label-success">{l s='ALLOW' mod='qloreservationpolicy'}</span>
                                    {else}
                                        <span class="label label-danger">{l s='DENY' mod='qloreservationpolicy'}</span>
                                    {/if}
                                </td>
                                <td>
                                    <strong><code>{$log.reason_code|escape:'html':'UTF-8'}</code></strong><br />
                                    <small>{$log.explanation|escape:'html':'UTF-8'}</small>
                                </td>
                                <td>
                                    <pre style="font-size: 11px; margin: 0; padding: 4px; max-height: 85px; overflow-y: auto; background-color: #f8f8f8;">{$log.facts_json|escape:'html':'UTF-8'}</pre>
                                </td>
                            </tr>
                        {/foreach}
                    </tbody>
                </table>
            </div>
            {if $totalAuditLogs > 5}
                <div style="padding: 10px 15px;">
                    <p class="text-muted" style="margin: 0;">
                        <i class="icon-info-sign"></i> {l s='Showing the 5 most recent evaluations. Download the JSON file to inspect the complete history (%d total records).' sprintf=[$totalAuditLogs] mod='qloreservationpolicy'}
                    </p>
                </div>
            {/if}
        {else}
            <div style="padding: 20px;">
                <div class="alert alert-info" style="margin-bottom: 0;">
                    <i class="icon-info-circle"></i> {l s='No evaluations logged yet. Run a simulation using the form above to generate audit entries.' mod='qloreservationpolicy'}
                </div>
            </div>
        {/if}
    </div>
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
    if (typeof $ !== 'undefined' && typeof $.fn.tooltip === 'function') {
        $('[data-toggle="tooltip"]').tooltip();
    }
});
</script>
