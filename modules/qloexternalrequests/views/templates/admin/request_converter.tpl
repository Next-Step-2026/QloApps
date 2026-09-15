{*
* NOTICE OF LICENSE
*
* This source file is subject to the Academic Free License (AFL 3.0)
* that is bundled with this package in the file LICENSE.txt.
*
* @author    QloApps Engineering
* @copyright Since 2010 QloApps
* @license   http://opensource.org/licenses/afl-3.0.php Academic Free License (AFL 3.0)
*}

<div class="panel" id="request-converter-form-panel">
    <div class="panel-heading">
        <i class="icon-random"></i> {l s='External Requests Canonical Converter' mod='qloexternalrequests'}
    </div>

    {if isset($conversionError) && $conversionError && (!isset($conversionResult) || !$conversionResult)}
        <div class="alert alert-warning">
            <h4><i class="icon-warning-sign"></i> {l s='Contingency Alert' mod='qloexternalrequests'}</h4>
            <p>{$conversionError|escape:'html':'UTF-8'}</p>
            {if isset($correlationId) && $correlationId}
                <p style="margin-top: 8px;">
                    <small><strong>{l s='Correlation ID:' mod='qloexternalrequests'}</strong> <code>{$correlationId|escape:'html':'UTF-8'}</code></small>
                </p>
            {/if}
        </div>
    {/if}

    <div class="alert alert-info">
        <i class="icon-info-circle"></i> {l s='Integration interface with the canonical reservation converter service (RFC-006). Select the external channel provider, provide the channel payload in JSON, and validate/convert it into the standardized QloApps canonical reservation draft.' mod='qloexternalrequests'}
    </div>

    <form method="post" action="{$actionUrl|escape:'html':'UTF-8'}" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3 required" for="provider_code">
                {l s='Source Channel (Provider):' mod='qloexternalrequests'}
            </label>
            <div class="col-lg-5">
                <select name="provider_code" id="provider_code" class="form-control">
                    <option value="PROVIDER_A"{if isset($selectedProvider) && $selectedProvider == 'PROVIDER_A'} selected="selected"{/if}>
                        {l s='Provider A (arrival + nights)' mod='qloexternalrequests'}
                    </option>
                    <option value="PROVIDER_B"{if isset($selectedProvider) && $selectedProvider == 'PROVIDER_B'} selected="selected"{/if}>
                        {l s='Provider B (checkin_date + checkout_date)' mod='qloexternalrequests'}
                    </option>
                </select>
                <p class="help-block">
                    {l s='Select the external partner platform or reservation payload schema.' mod='qloexternalrequests'}
                </p>
            </div>
        </div>

        <div class="form-group">
            <label class="control-label col-lg-3 required" for="raw_payload_json">
                {l s='Channel JSON Payload:' mod='qloexternalrequests'}
            </label>
            <div class="col-lg-8">
                <div class="btn-group" style="margin-bottom: 8px;">
                    <button type="button" class="btn btn-default btn-xs" id="btnSampleProviderA">
                        <i class="icon-magic"></i> {l s='Sample Provider A' mod='qloexternalrequests'}
                    </button>
                    <button type="button" class="btn btn-default btn-xs" id="btnSampleProviderB">
                        <i class="icon-magic"></i> {l s='Sample Provider B' mod='qloexternalrequests'}
                    </button>
                    <button type="button" class="btn btn-default btn-xs" id="btnClearPayload">
                        <i class="icon-trash"></i> {l s='Clear' mod='qloexternalrequests'}
                    </button>
                </div>
                <textarea name="raw_payload_json" id="raw_payload_json" rows="8" class="form-control" style="font-family: monospace; font-size: 12px;" placeholder='{literal}{"guest_full_name": "Maria Oliveira", "arrival": "2026-09-10", "nights": 3}{/literal}'>{if isset($rawPayloadJson)}{$rawPayloadJson|escape:'html':'UTF-8'}{/if}</textarea>
                <p class="help-block">
                    {l s='Paste the raw JSON payload received from the external channel partner.' mod='qloexternalrequests'}
                </p>
            </div>
        </div>

        <div class="panel-footer">
            {if (isset($rawPayloadJson) && $rawPayloadJson) || (isset($conversionResult) && $conversionResult)}
                <a href="{$resetUrl|escape:'html':'UTF-8'}" class="btn btn-default">
                    <i class="process-icon-cancel"></i> {l s='Reset' mod='qloexternalrequests'}
                </a>
            {/if}
            <button type="submit" name="submitConvertRequest" id="submitConvertRequest" class="btn btn-primary pull-right">
                <i class="process-icon-save"></i> {l s='Convert to Canonical Draft' mod='qloexternalrequests'}
            </button>
        </div>
    </form>
</div>

{if isset($conversionResult) && $conversionResult}
    {if isset($conversionResult.status) && $conversionResult.status == 'SUCCESS'}
        <div class="panel" id="canonical-draft-panel">
            <div class="panel-heading">
                <i class="icon-check-circle text-success"></i> {l s='Normalized Canonical Reservation Draft' mod='qloexternalrequests'}
                <span class="badge badge-success pull-right"><i class="icon-check"></i> {l s='Validated & Ready' mod='qloexternalrequests'}</span>
            </div>

            <div class="panel-body" style="padding-bottom: 0;">
                <div class="row" style="margin-bottom: 20px;">
                    <div class="col-sm-6 col-md-3">
                        <div class="well well-sm" style="margin-bottom: 10px; background-color: #f8fafc; border-left: 4px solid #72c02c;">
                            <div class="text-muted"><small><i class="icon-user"></i> {l s='Guest & Source' mod='qloexternalrequests'}</small></div>
                            <div style="font-size: 15px; font-weight: bold; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                                {$conversionResult.draft.guest_name|escape:'html':'UTF-8'}
                            </div>
                            <small><span class="label label-primary">{$conversionResult.draft.source_provider|escape:'html':'UTF-8'}</span></small>
                        </div>
                    </div>
                    <div class="col-sm-6 col-md-3">
                        <div class="well well-sm" style="margin-bottom: 10px; background-color: #f8fafc; border-left: 4px solid #2eacce;">
                            <div class="text-muted"><small><i class="icon-calendar"></i> {l s='Stay Period' mod='qloexternalrequests'}</small></div>
                            <div style="font-size: 13px; font-weight: bold;">
                                {$conversionResult.draft.check_in|escape:'html':'UTF-8'} &rarr; {$conversionResult.draft.check_out|escape:'html':'UTF-8'}
                            </div>
                            <small><span class="badge badge-success">{$conversionResult.draft.nights|intval} {l s='night(s)' mod='qloexternalrequests'}</span></small>
                        </div>
                    </div>
                    <div class="col-sm-6 col-md-3">
                        <div class="well well-sm" style="margin-bottom: 10px; background-color: #f8fafc; border-left: 4px solid #365d98;">
                            <div class="text-muted"><small><i class="icon-building"></i> {l s='Capacity & Channel Ref' mod='qloexternalrequests'}</small></div>
                            <div style="font-size: 14px; font-weight: bold;">
                                <span class="badge badge-info">{$conversionResult.draft.rooms_requested|intval} {l s='room(s)' mod='qloexternalrequests'}</span>
                            </div>
                            <small class="text-muted">{l s='Ref:' mod='qloexternalrequests'} <code>{$conversionResult.draft.channel_reference|escape:'html':'UTF-8'}</code></small>
                        </div>
                    </div>
                    <div class="col-sm-6 col-md-3">
                        <div class="well well-sm" style="margin-bottom: 10px; background-color: #f8fafc; border-left: 4px solid #6c757d;">
                            <div class="text-muted"><small><i class="icon-barcode"></i> {l s='Correlation ID' mod='qloexternalrequests'}</small></div>
                            <div style="font-size: 11px; font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="{$conversionResult.correlation_id|escape:'html':'UTF-8'}">
                                {$conversionResult.correlation_id|escape:'html':'UTF-8'}
                            </div>
                            <small><span class="label label-success">{l s='HTTP 200 OK' mod='qloexternalrequests'}</span></small>
                        </div>
                    </div>
                </div>

                <h4><i class="icon-table"></i> {l s='Normalized Field Specifications' mod='qloexternalrequests'}</h4>
                <div class="table-responsive">
                    <table class="table table-bordered table-striped table-hover">
                        <thead>
                            <tr class="active">
                                <th style="width: 22%;"><i class="icon-tag"></i> {l s='Canonical Field' mod='qloexternalrequests'}</th>
                                <th style="width: 20%;"><i class="icon-code"></i> {l s='JSON Key' mod='qloexternalrequests'}</th>
                                <th style="width: 33%;"><i class="icon-check"></i> {l s='Normalized Value' mod='qloexternalrequests'}</th>
                                <th style="width: 25%;"><i class="icon-info"></i> {l s='Type / Business Specification' mod='qloexternalrequests'}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td><i class="icon-user"></i> <strong>{l s='Guest Full Name' mod='qloexternalrequests'}</strong></td>
                                <td><code>guest_name</code></td>
                                <td><strong class="text-primary">{$conversionResult.draft.guest_name|escape:'html':'UTF-8'}</strong></td>
                                <td><span class="text-muted">{l s='String (Non-empty full name)' mod='qloexternalrequests'}</span></td>
                            </tr>
                            <tr>
                                <td><i class="icon-calendar"></i> <strong>{l s='Check-in Date' mod='qloexternalrequests'}</strong></td>
                                <td><code>check_in</code></td>
                                <td><span class="label label-info">{$conversionResult.draft.check_in|escape:'html':'UTF-8'}</span></td>
                                <td><span class="text-muted">{l s='ISO-8601 Date (YYYY-MM-DD)' mod='qloexternalrequests'}</span></td>
                            </tr>
                            <tr>
                                <td><i class="icon-calendar"></i> <strong>{l s='Check-out Date' mod='qloexternalrequests'}</strong></td>
                                <td><code>check_out</code></td>
                                <td><span class="label label-info">{$conversionResult.draft.check_out|escape:'html':'UTF-8'}</span></td>
                                <td><span class="text-muted">{l s='ISO-8601 Date (Calculated / validated)' mod='qloexternalrequests'}</span></td>
                            </tr>
                            <tr>
                                <td><i class="icon-moon"></i> <strong>{l s='Length of Stay' mod='qloexternalrequests'}</strong></td>
                                <td><code>nights</code></td>
                                <td><span class="badge badge-success">{$conversionResult.draft.nights|intval} {l s='night(s)' mod='qloexternalrequests'}</span></td>
                                <td><span class="text-muted">{l s='Positive Integer (>= 1 night)' mod='qloexternalrequests'}</span></td>
                            </tr>
                            <tr>
                                <td><i class="icon-building"></i> <strong>{l s='Rooms Requested' mod='qloexternalrequests'}</strong></td>
                                <td><code>rooms_requested</code></td>
                                <td><span class="badge badge-info">{$conversionResult.draft.rooms_requested|intval} {l s='room(s)' mod='qloexternalrequests'}</span></td>
                                <td><span class="text-muted">{l s='Positive Integer (>= 1 room)' mod='qloexternalrequests'}</span></td>
                            </tr>
                            <tr>
                                <td><i class="icon-barcode"></i> <strong>{l s='Channel Reference' mod='qloexternalrequests'}</strong></td>
                                <td><code>channel_reference</code></td>
                                <td><code>{$conversionResult.draft.channel_reference|escape:'html':'UTF-8'}</code></td>
                                <td><span class="text-muted">{l s='Unique channel booking reference' mod='qloexternalrequests'}</span></td>
                            </tr>
                            <tr>
                                <td><i class="icon-random"></i> <strong>{l s='Origin Channel' mod='qloexternalrequests'}</strong></td>
                                <td><code>source_provider</code></td>
                                <td><span class="label label-primary">{$conversionResult.draft.source_provider|escape:'html':'UTF-8'}</span></td>
                                <td><span class="text-muted">{l s='Partner identifier enum (PROVIDER_A | PROVIDER_B)' mod='qloexternalrequests'}</span></td>
                            </tr>
                            {if isset($conversionResult.correlation_id) && $conversionResult.correlation_id}
                            <tr>
                                <td><i class="icon-exchange"></i> <strong>{l s='Tracing Correlation ID' mod='qloexternalrequests'}</strong></td>
                                <td><code>correlation_id</code></td>
                                <td><code>{$conversionResult.correlation_id|escape:'html':'UTF-8'}</code></td>
                                <td><span class="text-muted">{l s='RFC 4122 UUID v4 (end-to-end tracing)' mod='qloexternalrequests'}</span></td>
                            </tr>
                            {/if}
                            <tr>
                                <td><i class="icon-check-circle"></i> <strong>{l s='Draft Status' mod='qloexternalrequests'}</strong></td>
                                <td><code>status</code></td>
                                <td><span class="label label-success">{$conversionResult.status|escape:'html':'UTF-8'}</span></td>
                                <td><span class="text-muted">{l s='Normalized Canonical Status' mod='qloexternalrequests'}</span></td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                {if isset($rawCanonicalJson) && $rawCanonicalJson}
                    <div style="margin-top: 15px; margin-bottom: 10px;">
                        <button type="button" class="btn btn-default btn-sm" id="btnToggleRawJson">
                            <i class="icon-code"></i> {l s='View Raw Canonical JSON' mod='qloexternalrequests'}
                        </button>
                        <button type="button" class="btn btn-default btn-sm" id="btnCopyCanonicalJson" style="margin-left: 5px;">
                            <i class="icon-copy"></i> {l s='Copy JSON' mod='qloexternalrequests'}
                        </button>
                        <span id="copyFeedback" class="label label-success" style="display: none; margin-left: 8px; font-size: 11px;">
                            <i class="icon-check"></i> {l s='Copied to clipboard!' mod='qloexternalrequests'}
                        </span>
                        <div id="canonicalJsonWrapper" style="display: none; margin-top: 10px;">
                            <pre id="canonicalJsonPre" style="background: #272822; color: #f8f8f2; padding: 12px; border-radius: 4px; font-family: monospace; font-size: 12px; max-height: 260px; overflow-y: auto;"><code id="canonicalJsonCode">{$rawCanonicalJson|escape:'html':'UTF-8'}</code></pre>
                        </div>
                    </div>
                {/if}
            </div>

            <div class="panel-footer">
                <a href="{$resetUrl|escape:'html':'UTF-8'}" class="btn btn-default">
                    <i class="process-icon-cancel"></i> {l s='New Conversion' mod='qloexternalrequests'}
                </a>
                <a href="{$bookingUrl|escape:'html':'UTF-8'}" class="btn btn-success pull-right" style="margin-left: 8px;" target="_blank">
                    <i class="icon-calendar"></i> {l s='Create Reservation in QloApps' mod='qloexternalrequests'} <i class="icon-external-link"></i>
                </a>
                <a href="{$ordersUrl|escape:'html':'UTF-8'}" class="btn btn-default pull-right" target="_blank">
                    <i class="icon-list-alt"></i> {l s='View Orders' mod='qloexternalrequests'} <i class="icon-external-link"></i>
                </a>
            </div>
        </div>
    {else}
        <div class="panel" id="canonical-errors-panel" style="border-left: 4px solid #d9534f;">
            <div class="panel-heading" style="color: #a94442;">
                <i class="icon-exclamation-triangle text-danger"></i> {l s='Canonical Validation Errors' mod='qloexternalrequests'}
                <span class="badge badge-danger pull-right">
                    {count($conversionResult.errors)} {l s='issue(s)' mod='qloexternalrequests'}
                </span>
            </div>

            <div class="panel-body">
                {if isset($conversionResult.correlation_id) && $conversionResult.correlation_id}
                    <div class="well well-sm" style="background-color: #fcf8e3; border: 1px solid #faebcc; margin-bottom: 12px;">
                        <i class="icon-barcode"></i> <strong>{l s='Tracing Correlation ID:' mod='qloexternalrequests'}</strong>
                        <code>{$conversionResult.correlation_id|escape:'html':'UTF-8'}</code>
                    </div>
                {/if}

                <div class="alert alert-danger">
                    <i class="icon-ban-circle"></i> {l s='The external request payload failed canonical validation. The fields listed below violate required business constraints and must be corrected before creating a reservation.' mod='qloexternalrequests'}
                </div>

                <div class="table-responsive">
                    <table class="table table-bordered table-striped">
                        <thead>
                            <tr class="danger">
                                <th style="width: 25%;"><i class="icon-tag"></i> {l s='Payload Field' mod='qloexternalrequests'}</th>
                                <th style="width: 25%;"><i class="icon-bell"></i> {l s='Error Code' mod='qloexternalrequests'}</th>
                                <th style="width: 50%;"><i class="icon-warning-sign"></i> {l s='Normalized Validation Message' mod='qloexternalrequests'}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {foreach from=$conversionResult.errors item=err}
                                <tr>
                                    <td><code>{$err.field|escape:'html':'UTF-8'}</code></td>
                                    <td><span class="label label-danger">{$err.error_code|escape:'html':'UTF-8'}</span></td>
                                    <td><strong>{$err.message|escape:'html':'UTF-8'}</strong></td>
                                </tr>
                            {/foreach}
                        </tbody>
                    </table>
                </div>
            </div>

            <div class="panel-footer">
                <a href="{$resetUrl|escape:'html':'UTF-8'}" class="btn btn-default">
                    <i class="process-icon-cancel"></i> {l s='Reset Form' mod='qloexternalrequests'}
                </a>
            </div>
        </div>
    {/if}
{/if}

{literal}
<script type="text/javascript">
$(document).ready(function () {
    var sampleProviderA = {
        "guest_full_name": "Maria Oliveira",
        "arrival": "2026-09-10",
        "nights": 3,
        "room_count": 1,
        "channel_reference": "REF-12345"
    };

    var sampleProviderB = {
        "customer": {
            "first_name": "John",
            "last_name": "Doe"
        },
        "checkin_date": "2026-10-01",
        "checkout_date": "2026-10-05",
        "room_count": 2,
        "reference_id": "REF-67890"
    };

    $('#btnSampleProviderA').on('click', function () {
        $('#provider_code').val('PROVIDER_A');
        $('#raw_payload_json').val(JSON.stringify(sampleProviderA, null, 2));
    });

    $('#btnSampleProviderB').on('click', function () {
        $('#provider_code').val('PROVIDER_B');
        $('#raw_payload_json').val(JSON.stringify(sampleProviderB, null, 2));
    });

    $('#btnClearPayload').on('click', function () {
        $('#raw_payload_json').val('').focus();
    });

    $('#btnToggleRawJson').on('click', function () {
        $('#canonicalJsonWrapper').slideToggle(200);
    });

    $('#btnCopyCanonicalJson').on('click', function () {
        var codeElement = document.getElementById('canonicalJsonCode');
        if (!codeElement) {
            return;
        }
        var textToCopy = codeElement.innerText || codeElement.textContent;

        if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
            navigator.clipboard.writeText(textToCopy).then(function () {
                showCopiedFeedback();
            }).catch(function () {
                fallbackCopy(textToCopy);
            });
        } else {
            fallbackCopy(textToCopy);
        }
    });

    function fallbackCopy(text) {
        var dummy = document.createElement('textarea');
        dummy.value = text;
        dummy.setAttribute('readonly', '');
        dummy.style.position = 'absolute';
        dummy.style.left = '-9999px';
        document.body.appendChild(dummy);
        dummy.select();
        document.execCommand('copy');
        document.body.removeChild(dummy);
        showCopiedFeedback();
    }

    function showCopiedFeedback() {
        var $fb = $('#copyFeedback');
        $fb.fadeIn(150);
        setTimeout(function () {
            $fb.fadeOut(300);
        }, 2500);
    }
});
</script>
{/literal}
