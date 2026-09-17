{*
* 2010-2026 QloApps
*
* NOTICE OF LICENSE
*
* This source file is subject to the Academic Free License (AFL 3.0)
* that is bundled with this package in the file LICENSE.txt.
* It is also available through the world-wide-web at this URL:
* http://opensource.org/licenses/afl-3.0.php
*}

<div class="panel">
    <div class="panel-heading">
        <i class="icon-magic"></i> {l s='Copiloto de Atendimento e Consulta de Reservas' mod='qloreservationassistant'}
    </div>

    {if isset($assistantError) && $assistantError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$assistantError|escape:'html':'UTF-8'}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">
                <span class="label-tooltip" data-toggle="tooltip" data-html="true" title="" data-original-title="{l s='Data base para o cálculo de expressões temporais relativas como hoje, amanhã ou próxima sexta.' mod='qloreservationassistant'}">
                    {l s='Data de Referência:' mod='qloreservationassistant'}
                </span>
            </label>
            <div class="col-lg-3">
                <input type="date" name="reference_date" value="{$currentRefDate|escape:'html':'UTF-8'}" class="form-control" required />
            </div>
        </div>

        <div class="form-group">
            <label class="control-label col-lg-3">
                {l s='Pergunta do Hóspede:' mod='qloreservationassistant'}
            </label>
            <div class="col-lg-7">
                <input type="text" name="user_query" value="{$lastUserQuery|escape:'html':'UTF-8'}" placeholder="{l s='Ex: Tem quarto deluxe para depois de amanhã para 2 pessoas?' mod='qloreservationassistant'}" class="form-control" maxlength="256" autofocus required />
            </div>
            <div class="col-lg-2">
                <button type="submit" name="submitQueryAssistant" class="btn btn-primary btn-block">
                    <i class="icon-magic"></i> {l s='Interpretar' mod='qloreservationassistant'}
                </button>
            </div>
        </div>
    </form>

    {if isset($assistantResult) && $assistantResult}
        <hr />
        <div class="well">
            <div class="row">
                <div class="col-lg-8">
                    <h4>
                        <i class="icon-lightbulb"></i> {l s='Interpretação do Assistente' mod='qloreservationassistant'}
                    </h4>
                </div>
                <div class="col-lg-4 text-right">
                    {if isset($assistantResult.correlation_id) && $assistantResult.correlation_id}
                        <small class="text-muted">
                            {l s='Rastreabilidade:' mod='qloreservationassistant'} <code>{$assistantResult.correlation_id|escape:'html':'UTF-8'}</code>
                        </small>
                    {/if}
                </div>
            </div>

            <div class="row" style="margin-top: 10px;">
                <div class="col-lg-12">
                    <p>
                        <strong>{l s='Intenção Classificada:' mod='qloreservationassistant'}</strong>
                        {if $assistantResult.intent == 'AVAILABILITY_QUERY'}
                            <span class="badge badge-success">{$assistantResult.intent|escape:'html':'UTF-8'}</span>
                        {elseif $assistantResult.intent == 'POLICY_QUERY'}
                            <span class="badge badge-info">{$assistantResult.intent|escape:'html':'UTF-8'}</span>
                        {elseif $assistantResult.intent == 'RESERVATION_LOOKUP'}
                            <span class="badge badge-primary">{$assistantResult.intent|escape:'html':'UTF-8'}</span>
                        {else}
                            <span class="badge badge-warning">{$assistantResult.intent|escape:'html':'UTF-8'}</span>
                        {/if}
                        <span class="text-muted">({l s='Confiança:' mod='qloreservationassistant'} {($assistantResult.confidence * 100)|string_format:"%.1f"}%)</span>
                    </p>

                    {if isset($assistantResult.explanation) && $assistantResult.explanation}
                        <p>
                            <strong>{l s='Explicação:' mod='qloreservationassistant'}</strong>
                            {$assistantResult.explanation|escape:'html':'UTF-8'}
                        </p>
                    {/if}

                    {if isset($assistantResult.slots) && $assistantResult.slots}
                        <div class="panel panel-default" style="margin-top: 15px;">
                            <div class="panel-heading">
                                <i class="icon-tags"></i> {l s='Parâmetros e Entidades Extraídas (Slots)' mod='qloreservationassistant'}
                            </div>
                            <div class="panel-body">
                                <dl class="dl-horizontal" style="margin-bottom: 0;">
                                    {if isset($assistantResult.slots.room_type) && $assistantResult.slots.room_type}
                                        <dt>{l s='Tipo de Quarto:' mod='qloreservationassistant'}</dt>
                                        <dd><span class="label label-success">{$assistantResult.slots.room_type|escape:'html':'UTF-8'}</span></dd>
                                    {/if}

                                    {if isset($assistantResult.slots.check_in) && $assistantResult.slots.check_in}
                                        <dt>{l s='Período Sugerido:' mod='qloreservationassistant'}</dt>
                                        <dd>
                                            <i class="icon-calendar"></i>
                                            <strong>{$assistantResult.slots.check_in|escape:'html':'UTF-8'}</strong>
                                            {if isset($assistantResult.slots.check_out) && $assistantResult.slots.check_out}
                                                {l s='até' mod='qloreservationassistant'} <strong>{$assistantResult.slots.check_out|escape:'html':'UTF-8'}</strong>
                                            {/if}
                                        </dd>
                                    {/if}

                                    {if isset($assistantResult.slots.guests) && $assistantResult.slots.guests}
                                        <dt>{l s='Hóspedes:' mod='qloreservationassistant'}</dt>
                                        <dd><i class="icon-user"></i> {$assistantResult.slots.guests|escape:'html':'UTF-8'} {l s='pessoa(s)' mod='qloreservationassistant'}</dd>
                                    {/if}

                                    {if isset($assistantResult.slots.policy_category) && $assistantResult.slots.policy_category}
                                        <dt>{l s='Categoria de Política:' mod='qloreservationassistant'}</dt>
                                        <dd><span class="label label-info">{$assistantResult.slots.policy_category|escape:'html':'UTF-8'}</span></dd>
                                    {/if}

                                    {if isset($assistantResult.slots.reservation_code) && $assistantResult.slots.reservation_code}
                                        <dt>{l s='Código de Reserva:' mod='qloreservationassistant'}</dt>
                                        <dd><span class="label label-primary">{$assistantResult.slots.reservation_code|escape:'html':'UTF-8'}</span></dd>
                                    {/if}
                                </dl>
                            </div>
                        </div>
                    {/if}
                </div>
            </div>
        </div>
    {/if}
</div>

{if isset($queryHistory) && $queryHistory|@count > 0}
    <div class="panel">
        <div class="panel-heading">
            <i class="icon-history"></i> {l s='Histórico de Auditoria da Sessão (Últimas 5 Consultas)' mod='qloreservationassistant'}
            <form method="post" action="" class="pull-right" style="margin: -2px 0 0 0;">
                <button type="submit" name="submitClearHistory" class="btn btn-default btn-xs">
                    <i class="icon-trash"></i> {l s='Limpar Histórico' mod='qloreservationassistant'}
                </button>
            </form>
        </div>
        <div class="table-responsive">
            <table class="table table-striped table-hover">
                <thead>
                    <tr>
                        <th style="width: 90px;">{l s='Hora' mod='qloreservationassistant'}</th>
                        <th>{l s='Pergunta Submetida' mod='qloreservationassistant'}</th>
                        <th style="width: 180px;">{l s='Intenção' mod='qloreservationassistant'}</th>
                        <th style="width: 100px;">{l s='Confiança' mod='qloreservationassistant'}</th>
                        <th style="width: 220px;">{l s='Correlation ID' mod='qloreservationassistant'}</th>
                    </tr>
                </thead>
                <tbody>
                    {foreach from=$queryHistory item=item}
                        <tr>
                            <td><span class="text-muted">{$item.time|escape:'html':'UTF-8'}</span></td>
                            <td><strong>{$item.query|escape:'html':'UTF-8'}</strong></td>
                            <td>
                                {if $item.intent == 'AVAILABILITY_QUERY'}
                                    <span class="badge badge-success">{$item.intent|escape:'html':'UTF-8'}</span>
                                {elseif $item.intent == 'POLICY_QUERY'}
                                    <span class="badge badge-info">{$item.intent|escape:'html':'UTF-8'}</span>
                                {elseif $item.intent == 'RESERVATION_LOOKUP'}
                                    <span class="badge badge-primary">{$item.intent|escape:'html':'UTF-8'}</span>
                                {else}
                                    <span class="badge badge-warning">{$item.intent|escape:'html':'UTF-8'}</span>
                                {/if}
                            </td>
                            <td>{($item.confidence * 100)|string_format:"%.1f"}%</td>
                            <td><code>{$item.correlation_id|escape:'html':'UTF-8'}</code></td>
                        </tr>
                    {/foreach}
                </tbody>
            </table>
        </div>
    </div>
{/if}
