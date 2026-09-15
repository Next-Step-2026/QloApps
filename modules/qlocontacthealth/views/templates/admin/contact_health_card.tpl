<div class="panel">
    <div class="panel-heading">
        <i class="icon-user-md"></i> {l s='Indicador de Saúde e Higiene Cadastral' mod='qlocontacthealth'}
    </div>

    {if isset($healthWarning) && $healthWarning}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$healthWarning|escape:'html':'UTF-8'}
        </div>
    {/if}

    {if isset($contactHealth) && $contactHealth}
        <div class="row">
            <div class="col-lg-4">
                <h4>{l s='Score de Higiene:' mod='qlocontacthealth'}</h4>
                <div class="progress">
                    <div class="progress-bar {if $contactHealth.hygiene_score >= 80}progress-bar-success{elseif $contactHealth.hygiene_score >= 50}progress-bar-warning{else}progress-bar-danger{/if}" 
                         style="width: {$contactHealth.hygiene_score|intval}%">
                        {$contactHealth.hygiene_score|intval}/100
                    </div>
                </div>
                <p>
                    <strong>{l s='Status Geral:' mod='qlocontacthealth'}</strong> 
                    <span class="label {if $contactHealth.overall_status == 'FRESH'}label-success{elseif $contactHealth.overall_status == 'AGING'}label-warning{else}label-danger{/if}">
                        {$contactHealth.overall_status|escape:'html':'UTF-8'}
                    </span>
                </p>
                <p>
                    <strong>{l s='Consentimento LGPD:' mod='qlocontacthealth'}</strong> 
                    {if $contactHealth.consent_valid}
                        <span class="badge badge-success">{l s='VIGENTE' mod='qlocontacthealth'}</span>
                    {else}
                        <span class="badge badge-danger">{l s='EXPIRADO' mod='qlocontacthealth'}</span>
                    {/if}
                </p>
            </div>

            <div class="col-lg-8">
                <h4>{l s='Fatores de Contato:' mod='qlocontacthealth'}</h4>
                <table class="table table-bordered">
                    <thead>
                        <tr>
                            <th>{l s='Canal' mod='qlocontacthealth'}</th>
                            <th>{l s='Valor Mascarado' mod='qlocontacthealth'}</th>
                            <th>{l s='Status' mod='qlocontacthealth'}</th>
                            <th>{l s='Última Validação' mod='qlocontacthealth'}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {if isset($contactHealth.factors) && $contactHealth.factors}
                            {foreach from=$contactHealth.factors item=factor}
                                <tr>
                                    <td><strong>{$factor.type|escape:'html':'UTF-8'}</strong></td>
                                    <td><code>{$factor.value_masked|escape:'html':'UTF-8'}</code></td>
                                    <td>
                                        <span class="label {if $factor.status == 'FRESH'}label-success{elseif $factor.status == 'AGING'}label-warning{else}label-danger{/if}">
                                            {$factor.status|escape:'html':'UTF-8'}
                                        </span>
                                    </td>
                                    <td>
                                        {l s='%d dias atrás' sprintf=[$factor.days_since_verification|intval] mod='qlocontacthealth'}
                                    </td>
                                </tr>
                            {/foreach}
                        {/if}
                    </tbody>
                </table>

                {if isset($contactHealth.recommended_action) && $contactHealth.recommended_action != 'NONE'}
                    <div id="reconfirmation-alert-container"></div>
                    <button type="button" class="btn btn-warning" id="btn-simulate-reconfirmation" onclick="simulateContactReconfirmation({$customerId|intval});">
                        <i class="icon-envelope"></i> {l s='Simular Validação de Contato' mod='qlocontacthealth'}
                    </button>
                {/if}
            </div>
        </div>
    {/if}
</div>

<script type="text/javascript">
function simulateContactReconfirmation(customerId) {
    if (typeof $ === 'undefined') {
        return;
    }
    var $btn = $('#btn-simulate-reconfirmation');
    $btn.prop('disabled', true);

    $.ajax({
        type: 'POST',
        url: '{$ajaxUrl|escape:'javascript':'UTF-8'}',
        data: {
            ajax: 1,
            action: 'simulateReconfirmation',
            id_customer: customerId,
            token: '{$ajaxToken|escape:'javascript':'UTF-8'}'
        },
        dataType: 'json',
        success: function(response) {
            $btn.prop('disabled', false);
            if (response && response.success) {
                var html = '<div class="alert alert-success"><i class="icon-ok-sign"></i> ' + response.message + '</div>';
                $('#reconfirmation-alert-container').html(html);
                setTimeout(function() {
                    location.reload();
                }, 1500);
            } else {
                var msg = (response && response.message) ? response.message : '{l s='Erro ao processar a simulação.' mod='qlocontacthealth' js=1}';
                var html = '<div class="alert alert-danger"><i class="icon-exclamation-sign"></i> ' + msg + '</div>';
                $('#reconfirmation-alert-container').html(html);
            }
        },
        error: function() {
            $btn.prop('disabled', false);
            var html = '<div class="alert alert-danger"><i class="icon-exclamation-sign"></i> {l s='Erro de comunicação ao simular a validação.' mod='qlocontacthealth' js=1}</div>';
            $('#reconfirmation-alert-container').html(html);
        }
    });
}
</script>
