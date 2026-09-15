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

<div class="panel">
    <div class="panel-heading">
        <i class="icon-random"></i> {l s='External Requests Canonical Converter' mod='qloexternalrequests'}
    </div>

    {if isset($conversionError) && $conversionError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$conversionError|escape:'html':'UTF-8'}
        </div>
    {/if}

    <div class="alert alert-info">
        <i class="icon-info-circle"></i> {l s='Integration module with the canonical converter service (RFC-006). Select the source channel, paste the raw JSON request payload, and convert it into the QloApps canonical format.' mod='qloexternalrequests'}
    </div>

    <form method="post" action="{$actionUrl|escape:'html':'UTF-8'}" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3 required">
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
                    {l s='Select the external partner platform or reservation format.' mod='qloexternalrequests'}
                </p>
            </div>
        </div>

        <div class="form-group">
            <label class="control-label col-lg-3 required">
                {l s='Channel JSON Payload:' mod='qloexternalrequests'}
            </label>
            <div class="col-lg-7">
                <textarea name="raw_payload_json" id="raw_payload_json" rows="8" class="form-control" placeholder='{literal}{"guest_full_name": "Maria Oliveira", "arrival": "2026-09-10", "nights": 3}{/literal}'>{if isset($rawPayloadJson)}{$rawPayloadJson|escape:'html':'UTF-8'}{/if}</textarea>
                <p class="help-block">
                    {l s='Paste the raw JSON payload received from the external partner.' mod='qloexternalrequests'}
                </p>
            </div>
        </div>

        <div class="panel-footer">
            <button type="submit" name="submitConvertRequest" id="submitConvertRequest" class="btn btn-primary pull-right">
                <i class="process-icon-save"></i> {l s='Convert to Canonical Draft' mod='qloexternalrequests'}
            </button>
        </div>
    </form>

    {if isset($conversionResult) && $conversionResult}
        <hr />
        {if isset($conversionResult.status) && $conversionResult.status == 'SUCCESS'}
            <div class="alert alert-success">
                <h4><i class="icon-check"></i> {l s='Generated Canonical Draft:' mod='qloexternalrequests'}</h4>
                <table class="table table-bordered">
                    <tr>
                        <th style="width: 25%;">{l s='Guest Name:' mod='qloexternalrequests'}</th>
                        <td><strong>{$conversionResult.draft.guest_name|escape:'html':'UTF-8'}</strong></td>
                    </tr>
                    <tr>
                        <th>{l s='Check-in Date:' mod='qloexternalrequests'}</th>
                        <td>{$conversionResult.draft.check_in|escape:'html':'UTF-8'}</td>
                    </tr>
                    <tr>
                        <th>{l s='Check-out Date:' mod='qloexternalrequests'}</th>
                        <td>{$conversionResult.draft.check_out|escape:'html':'UTF-8'}</td>
                    </tr>
                    <tr>
                        <th>{l s='Nights:' mod='qloexternalrequests'}</th>
                        <td>{$conversionResult.draft.nights|intval} {l s='night(s)' mod='qloexternalrequests'}</td>
                    </tr>
                    <tr>
                        <th>{l s='Requested Rooms:' mod='qloexternalrequests'}</th>
                        <td>{$conversionResult.draft.rooms_requested|intval}</td>
                    </tr>
                    <tr>
                        <th>{l s='Channel Reference:' mod='qloexternalrequests'}</th>
                        <td><code>{$conversionResult.draft.channel_reference|escape:'html':'UTF-8'}</code></td>
                    </tr>
                    <tr>
                        <th>{l s='Source Channel:' mod='qloexternalrequests'}</th>
                        <td><span class="badge">{$conversionResult.draft.source_provider|escape:'html':'UTF-8'}</span></td>
                    </tr>
                </table>
            </div>
        {else}
            <div class="alert alert-danger">
                <h4><i class="icon-remove"></i> {l s='Conversion Failed:' mod='qloexternalrequests'}</h4>
                {if isset($conversionResult.errors) && $conversionResult.errors}
                    <ul>
                        {foreach from=$conversionResult.errors item=err}
                            <li><strong>{$err.field|escape:'html':'UTF-8'}:</strong> {$err.message|escape:'html':'UTF-8'} (<code>{$err.error_code|escape:'html':'UTF-8'}</code>)</li>
                        {/foreach}
                    </ul>
                {/if}
            </div>
        {/if}
    {/if}
</div>
