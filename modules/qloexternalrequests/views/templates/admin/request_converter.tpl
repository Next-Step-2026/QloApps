{*
* NOTICE OF LICENSE
*
* This source file is subject to the Academic Free License (AFL 3.0)
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
        <i class="icon-info-circle"></i> {l s='Módulo de integração com o serviço conversor canônico (RFC-006). Selecione o canal de origem e informe o payload JSON da solicitação.' mod='qloexternalrequests'}
    </div>
</div>
