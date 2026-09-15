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
        <i class="icon-random"></i> {l s='Conversor Canônico de Solicitações Externas' mod='qloexternalrequests'}
    </div>

    {if isset($conversionError) && $conversionError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$conversionError|escape:'html':'UTF-8'}
        </div>
    {/if}

    <div class="alert alert-info">
        <i class="icon-info-circle"></i> {l s='Módulo de integração com o serviço conversor canônico (RFC-006). Selecione o canal de origem, cole o corpo JSON bruto da solicitação externa e execute a conversão para o formato padrão do QloApps.' mod='qloexternalrequests'}
    </div>

    <form method="post" action="{$actionUrl|escape:'html':'UTF-8'}" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3 required">
                {l s='Canal de Origem (Provedor):' mod='qloexternalrequests'}
            </label>
            <div class="col-lg-5">
                <select name="provider_code" id="provider_code" class="form-control">
                    <option value="PROVIDER_A"{if isset($selectedProvider) && $selectedProvider == 'PROVIDER_A'} selected="selected"{/if}>
                        {l s='Provedor A (arrival + nights)' mod='qloexternalrequests'}
                    </option>
                    <option value="PROVIDER_B"{if isset($selectedProvider) && $selectedProvider == 'PROVIDER_B'} selected="selected"{/if}>
                        {l s='Provedor B (checkin_date + checkout_date)' mod='qloexternalrequests'}
                    </option>
                </select>
                <p class="help-block">
                    {l s='Selecione a plataforma ou formato do parceiro de origem da reserva.' mod='qloexternalrequests'}
                </p>
            </div>
        </div>

        <div class="form-group">
            <label class="control-label col-lg-3 required">
                {l s='Payload JSON do Canal:' mod='qloexternalrequests'}
            </label>
            <div class="col-lg-7">
                <textarea name="raw_payload_json" id="raw_payload_json" rows="8" class="form-control" placeholder='{literal}{"guest_full_name": "Maria Oliveira", "arrival": "2026-09-10", "nights": 3}{/literal}'>{if isset($rawPayloadJson)}{$rawPayloadJson|escape:'html':'UTF-8'}{/if}</textarea>
                <p class="help-block">
                    {l s='Cole o corpo JSON bruto recebido do parceiro comercial externo.' mod='qloexternalrequests'}
                </p>
            </div>
        </div>

        <div class="panel-footer">
            <button type="submit" name="submitConvertRequest" id="submitConvertRequest" class="btn btn-primary pull-right">
                <i class="process-icon-save"></i> {l s='Converter para Rascunho Canônico' mod='qloexternalrequests'}
            </button>
        </div>
    </form>
</div>
