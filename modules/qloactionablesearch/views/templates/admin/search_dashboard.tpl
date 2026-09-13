{*
* Search Dashboard Template for QloApps Back Office (QLO-FEAT-008)
*}

<div class="panel">
    <div class="panel-heading">
        <i class="icon-search"></i> {l s='Fast Lexical Search Engine & Entity Recognition' mod='qloactionablesearch'}
    </div>

    <div class="alert alert-info">
        <i class="icon-info-sign"></i> {l s='Enter natural language queries combining room type, amenities, and occupants (e.g.: "suite com vista mar 2 adultos", "quarto com ar condicionado"). The C++ engine parses and normalizes terms in milliseconds.' mod='qloactionablesearch'}
    </div>

    {if $searchError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> <strong>{l s='Contingency Notice:' mod='qloactionablesearch'}</strong> {$searchError|escape:'html':'UTF-8'}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">
                <span class="label-tooltip" data-toggle="tooltip" data-html="true" title="{l s='Enter terms like: suite mar 2 pessoas, quarto standard, com banheira' mod='qloactionablesearch'}">
                    {l s='Free Text Query:' mod='qloactionablesearch'}
                </span>
            </label>
            <div class="col-lg-6">
                <div class="input-group">
                    <input type="text" name="search_query" id="search_query" value="{$searchQuery|escape:'html':'UTF-8'}" 
                           placeholder="{l s='e.g.: suite com vista para o mar 2 adultos' mod='qloactionablesearch'}" 
                           class="form-control" autofocus required />
                    <span class="input-group-addon"><i class="icon-terminal"></i></span>
                </div>
            </div>
            <div class="col-lg-3">
                <button type="submit" name="submitSearchQuery" class="btn btn-primary btn-block">
                    <i class="icon-search"></i> {l s='Search Entities' mod='qloactionablesearch'}
                </button>
            </div>
        </div>
    </form>

    {if $searchResult}
        <hr />
        <div class="well">
            <div class="row">
                <div class="col-md-6">
                    <h4><i class="icon-tags"></i> {l s='Identified Entities & Terms:' mod='qloactionablesearch'}</h4>
                    <p>
                        <strong>{l s='Matched Normalized Tokens:' mod='qloactionablesearch'}</strong>
                        {if !empty($searchResult.tokens_matched)}
                            {foreach from=$searchResult.tokens_matched item=token}
                                <span class="badge badge-info" style="font-size: 13px; margin: 2px;">{$token|escape:'html':'UTF-8'}</span>
                            {/foreach}
                        {else}
                            <span class="text-muted">{l s='No catalog terms directly matched.' mod='qloactionablesearch'}</span>
                        {/if}
                    </p>

                    <p>
                        <strong>{l s='Extracted Capacity Filter:' mod='qloactionablesearch'}</strong>
                        {if isset($searchResult.extracted_filters.adults) && $searchResult.extracted_filters.adults}
                            <span class="label label-primary" style="font-size: 13px;">
                                <i class="icon-user"></i> {$searchResult.extracted_filters.adults|intval} {l s='Adult(s)' mod='qloactionablesearch'}
                            </span>
                        {else}
                            <span class="text-muted">{l s='Not specified in query' mod='qloactionablesearch'}</span>
                        {/if}
                    </p>

                    {if !empty($searchResult.extracted_filters.amenities)}
                        <p>
                            <strong>{l s='Detected Amenities:' mod='qloactionablesearch'}</strong>
                            {foreach from=$searchResult.extracted_filters.amenities item=amenity}
                                <span class="label label-success" style="font-size: 12px; margin: 2px;">
                                    <i class="icon-check"></i> {$amenity|escape:'html':'UTF-8'}
                                </span>
                            {/foreach}
                        </p>
                    {/if}

                    <p class="text-muted" style="font-size: 11px;">
                        Correlation ID: <code>{$searchResult.correlation_id|escape:'html':'UTF-8'}</code>
                    </p>
                </div>

                <div class="col-md-6">
                    <h4>
                        <i class="icon-building"></i> 
                        {l s='Matching Accommodations' mod='qloactionablesearch'} 
                        <span class="badge badge-success">{$searchResult.total_matches|intval}</span>
                    </h4>

                    {if $searchResult.total_matches > 0}
                        <div class="list-group">
                            {foreach from=$searchResult.matching_entity_ids item=entityId}
                                <div class="list-group-item">
                                    <div class="pull-right">
                                        <span class="badge badge-default">{$entityId|escape:'html':'UTF-8'}</span>
                                    </div>
                                    <h4 class="list-group-item-heading text-success">
                                        <i class="icon-check-circle"></i> 
                                        {if $entityId == 'room-suite-01'}
                                            Suíte Master Vista Mar
                                        {elseif $entityId == 'room-std-02'}
                                            Quarto Standard Casal
                                        {elseif $entityId == 'room-sgl-03'}
                                            Quarto Single Individual
                                        {else}
                                            {$entityId|escape:'html':'UTF-8'}
                                        {/if}
                                    </h4>
                                    <p class="list-group-item-text text-muted">
                                        {if $entityId == 'room-suite-01'}
                                            Capacity: 2 Adults | Amenities: Vista Mar, Ar Condicionado, Banheira
                                        {elseif $entityId == 'room-std-02'}
                                            Capacity: 2 Adults | Amenities: Ar Condicionado
                                        {elseif $entityId == 'room-sgl-03'}
                                            Capacity: 1 Adult | Amenities: Ventilador
                                        {/if}
                                    </p>
                                </div>
                            {/foreach}
                        </div>
                    {else}
                        <div class="alert alert-warning" style="margin-top: 10px;">
                            <i class="icon-info-circle"></i> {l s='No room matched all simultaneous search criteria.' mod='qloactionablesearch'}
                        </div>
                    {/if}
                </div>
            </div>
        </div>
    {/if}

    <div class="panel-footer">
        <span class="text-muted">
            <i class="icon-cogs"></i> {l s='C++ Engine listening on' mod='qloactionablesearch'} <code>http://127.0.0.1:8108</code> | SLA &lt; 5ms
        </span>
    </div>
</div>
