<div class="panel">
    <div class="panel-heading">
        <i class="icon-camera"></i> {l s='Visual Inspection & Housekeeping Photo Evidences' mod='qlovisualinspection'}
    </div>

    {if isset($inspectionError) && $inspectionError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$inspectionError|escape:'html':'UTF-8'}
        </div>
    {/if}

    <form method="post" action="" enctype="multipart/form-data" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3 required">
                {l s='Inspected Room:' mod='qlovisualinspection'}
            </label>
            <div class="col-lg-5">
                <select name="room_id" class="form-control" required>
                    {foreach from=$roomsList item=room}
                        <option value="{$room.id|escape:'html':'UTF-8'}" {if isset($selectedRoomId) && $selectedRoomId == $room.id}selected="selected"{/if}>
                            {$room.name|escape:'html':'UTF-8'}
                        </option>
                    {/foreach}
                </select>
            </div>
        </div>

        <hr style="margin: 15px 0;" />

        <div class="form-group">
            <label class="control-label col-lg-3">
                <strong>{l s='Evidence per Checklist Item:' mod='qlovisualinspection'}</strong>
            </label>
            <div class="col-lg-8">
                <p class="text-muted" style="margin-bottom: 15px;">
                    {l s='Upload a clear, sharp photo for each required room item (JPEG/PNG, max 5 MB per photo).' mod='qlovisualinspection'}
                </p>

                <div class="row">
                    <!-- Item 1: Bed & Linen -->
                    <div class="col-md-4">
                        <div class="panel panel-default">
                            <div class="panel-heading" style="font-size: 0.95em; font-weight: bold;">
                                <i class="icon-bookmark"></i> 1. {l s='Bed & Linen' mod='qlovisualinspection'} <span class="text-danger">*</span>
                            </div>
                            <div class="panel-body">
                                <p class="small text-muted">{l s='Made bed, straightened sheets, and aligned pillows.' mod='qlovisualinspection'}</p>
                                <input type="file" name="photo_bed" accept="image/jpeg,image/png" class="form-control" required />
                            </div>
                        </div>
                    </div>

                    <!-- Item 2: Sanitized Bathroom -->
                    <div class="col-md-4">
                        <div class="panel panel-default">
                            <div class="panel-heading" style="font-size: 0.95em; font-weight: bold;">
                                <i class="icon-tint"></i> 2. {l s='Sanitized Bathroom' mod='qlovisualinspection'} <span class="text-danger">*</span>
                            </div>
                            <div class="panel-body">
                                <p class="small text-muted">{l s='Clean countertop, streak-free mirror, and folded towels.' mod='qlovisualinspection'}</p>
                                <input type="file" name="photo_bath" accept="image/jpeg,image/png" class="form-control" required />
                            </div>
                        </div>
                    </div>

                    <!-- Item 3: Replenished Amenities -->
                    <div class="col-md-4">
                        <div class="panel panel-default">
                            <div class="panel-heading" style="font-size: 0.95em; font-weight: bold;">
                                <i class="icon-gift"></i> 3. {l s='Replenished Amenities' mod='qlovisualinspection'} <span class="text-danger">*</span>
                            </div>
                            <div class="panel-body">
                                <p class="small text-muted">{l s='Arranged soaps, shampoos, and complimentary toiletries.' mod='qlovisualinspection'}</p>
                                <input type="file" name="photo_amenities" accept="image/jpeg,image/png" class="form-control" required />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="form-group">
            <div class="col-lg-8 col-lg-offset-3">
                <button type="submit" name="submitInspection" class="btn btn-primary btn-lg">
                    <i class="icon-upload"></i> {l s='Save Evidences' mod='qlovisualinspection'}
                </button>
            </div>
        </div>
    </form>

    {if isset($itemsResults) && $itemsResults}
        <hr />
        
        <!-- Master Room Verdict Banner -->
        <div class="alert {if $overallAssessment == 'EVIDENCE_VALID'}alert-success{else}alert-danger{/if}" style="font-size: 1.15em;">
            <i class="{if $overallAssessment == 'EVIDENCE_VALID'}icon-check-circle{else}icon-exclamation-triangle{/if}"></i>
            <strong>{l s='Overall Inspection Result:' mod='qlovisualinspection'}</strong>
            {if $overallAssessment == 'EVIDENCE_VALID'}
                <span class="label label-success" style="font-size: 0.9em; margin-left: 10px; padding: 4px 10px;">
                    {l s='ALL EVIDENCES VALID' mod='qlovisualinspection'}
                </span>
                <span style="margin-left: 10px;">{l s='The room meets objective quality standards and photographic compliance.' mod='qlovisualinspection'}</span>
            {else}
                <span class="label label-danger" style="font-size: 0.9em; margin-left: 10px; padding: 4px 10px;">
                    {l s='RETAKE NON-COMPLIANT PHOTOS' mod='qlovisualinspection'}
                </span>
                <span style="margin-left: 10px;">{l s='One or more items did not meet minimum sharpness or lighting thresholds.' mod='qlovisualinspection'}</span>
            {/if}
        </div>

        <!-- Individual Item Audit Cards -->
        <div class="row">
            {foreach from=$itemsResults key=itemKey item=itemData}
                <div class="col-md-4">
                    <div class="panel {if isset($itemData.result.assessment) && $itemData.result.assessment == 'EVIDENCE_VALID'}panel-success{else}panel-danger{/if}">
                        <div class="panel-heading" style="font-weight: bold;">
                            <i class="{$itemData.icon|escape:'html':'UTF-8'}"></i> {$itemData.title|escape:'html':'UTF-8'}
                            <span class="pull-right">
                                {if isset($itemData.result.assessment) && $itemData.result.assessment == 'EVIDENCE_VALID'}
                                    <span class="badge badge-success">{l s='VALID' mod='qlovisualinspection'}</span>
                                {elseif isset($itemData.result.assessment)}
                                    <span class="badge badge-danger">{l s='RETAKE' mod='qlovisualinspection'}</span>
                                {else}
                                    <span class="badge badge-warning">{l s='NO METRICS' mod='qlovisualinspection'}</span>
                                {/if}
                            </span>
                        </div>
                        <div class="panel-body text-center" style="background: #fafafa;">
                            {if isset($itemData.preview_image) && $itemData.preview_image}
                                <div style="margin-bottom: 10px;">
                                    <a href="{$moduleImgUri}{$itemData.preview_image|escape:'html':'UTF-8'}" target="_blank" title="{l s='View full resolution' mod='qlovisualinspection'}">
                                        <img src="{$moduleImgUri}{$itemData.preview_image|escape:'html':'UTF-8'}" alt="{$itemData.title|escape:'html':'UTF-8'}" loading="lazy" style="max-width: 100%; height: auto; max-height: 180px; border-radius: 4px; border: 1px solid #ddd; object-fit: cover;" />
                                    </a>
                                </div>
                            {/if}

                            {if isset($itemData.result) && $itemData.result}
                                <div class="row" style="margin-top: 10px;">
                                    <div class="col-xs-6" style="padding: 2px;">
                                        <div class="well well-sm" style="margin-bottom: 5px; background: #fff;">
                                            <div class="text-muted"><small>{l s='Luminance' mod='qlovisualinspection'}</small></div>
                                            <strong>{$itemData.result.metrics.luminance|string_format:"%.1f"}</strong>
                                            <div>
                                                {if $itemData.result.metrics.luminance_status == 'OPTIMAL'}
                                                    <span class="label label-success">{l s='Optimal' mod='qlovisualinspection'}</span>
                                                {elseif $itemData.result.metrics.luminance_status == 'UNDEREXPOSED'}
                                                    <span class="label label-danger">{l s='Underexposed' mod='qlovisualinspection'}</span>
                                                {else}
                                                    <span class="label label-warning">{l s='Overexposed' mod='qlovisualinspection'}</span>
                                                {/if}
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-xs-6" style="padding: 2px;">
                                        <div class="well well-sm" style="margin-bottom: 5px; background: #fff;">
                                            <div class="text-muted"><small>{l s='Sharpness' mod='qlovisualinspection'}</small></div>
                                            <strong>{$itemData.result.metrics.sharpness_score|string_format:"%.1f"}</strong>
                                            <div>
                                                {if $itemData.result.metrics.sharpness_status == 'SHARP'}
                                                    <span class="label label-success">{l s='Sharp' mod='qlovisualinspection'}</span>
                                                {else}
                                                    <span class="label label-danger">{l s='Blurry' mod='qlovisualinspection'}</span>
                                                {/if}
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div class="text-muted" style="margin-top: 4px;">
                                    <small>{$itemData.result.metrics.width|intval} &times; {$itemData.result.metrics.height|intval} px</small>
                                </div>

                                {if isset($itemData.result.warnings) && $itemData.result.warnings|@count > 0}
                                    <div class="text-left" style="margin-top: 8px; margin-bottom: 0; padding: 6px 10px; font-size: 0.82em; background-color: #fdf2f2; border: 1px solid #f8d7da; border-left: 3px solid #d9534f; border-radius: 3px; color: #a94442;">
                                        <div style="font-weight: bold; margin-bottom: 3px;">
                                            <i class="icon-warning-sign"></i> {l s='Required adjustments:' mod='qlovisualinspection'}
                                        </div>
                                        <ul style="padding-left: 15px; margin-bottom: 0;">
                                            {foreach from=$itemData.result.warnings item=warn}
                                                <li>
                                                    {if $warn == 'LOW_RESOLUTION'}
                                                        {l s='Insufficient resolution (min. 800x600 px).' mod='qlovisualinspection'}
                                                    {elseif $warn == 'UNDEREXPOSED'}
                                                        {l s='Photo is too dark. Turn on room lights.' mod='qlovisualinspection'}
                                                    {elseif $warn == 'OVEREXPOSED'}
                                                        {l s='Photo is overexposed to light.' mod='qlovisualinspection'}
                                                    {elseif $warn == 'BLURRY_IMAGE'}
                                                        {l s='Photo is blurry or shaky.' mod='qlovisualinspection'}
                                                    {else}
                                                        {$warn|escape:'html':'UTF-8'}
                                                    {/if}
                                                </li>
                                            {/foreach}
                                        </ul>
                                    </div>
                                {/if}
                            {elseif isset($itemData.error) && $itemData.error}
                                <div class="text-left" style="margin-top: 10px; margin-bottom: 0; padding: 6px 10px; font-size: 0.82em; background-color: #fcf8e3; border: 1px solid #faebcc; border-left: 3px solid #f0ad4e; border-radius: 3px; color: #8a6d3b;">
                                    <i class="icon-exclamation-triangle"></i> {$itemData.error|escape:'html':'UTF-8'}
                                </div>
                            {/if}
                        </div>
                    </div>
                </div>
            {/foreach}
        </div>
    {/if}
</div>

<!-- Fernando's View: Histórico de Auditoria de Inspeções -->
<div class="panel">
    <div class="panel-heading">
        <i class="icon-list-alt"></i> {l s='Housekeeping Governance Audit Trail (Manager View)' mod='qlovisualinspection'}
        {if isset($recentInspections) && $recentInspections|@count > 0}
            <span class="badge">{$recentInspections|@count} {l s='records' mod='qlovisualinspection'}</span>
        {/if}
    </div>

    {if isset($recentInspections) && $recentInspections|@count > 0}
        <div class="table-responsive">
            <table class="table table-bordered table-striped table-hover">
                <thead>
                    <tr class="nodrag nodrop">
                        <th class="text-center" style="width: 70px;">{l s='Evidence' mod='qlovisualinspection'}</th>
                        <th>{l s='Room' mod='qlovisualinspection'}</th>
                        <th>{l s='Inspected Item' mod='qlovisualinspection'}</th>
                        <th class="text-center">{l s='Verdict' mod='qlovisualinspection'}</th>
                        <th class="text-center">{l s='Luminance' mod='qlovisualinspection'}</th>
                        <th class="text-center">{l s='Sharpness' mod='qlovisualinspection'}</th>
                        <th class="text-center">{l s='Dimensions' mod='qlovisualinspection'}</th>
                        <th>{l s='Auditor / Inspector' mod='qlovisualinspection'}</th>
                        <th class="text-center">{l s='Date & Time' mod='qlovisualinspection'}</th>
                    </tr>
                </thead>
                <tbody>
                    {foreach from=$recentInspections item=row}
                        <tr>
                            <td class="text-center">
                                {if !empty($row.image_path)}
                                    <a href="{$moduleImgUri}{$row.image_path|escape:'html':'UTF-8'}" target="_blank" title="{l s='View original size' mod='qlovisualinspection'}">
                                        <img src="{$moduleImgUri}{$row.image_path|escape:'html':'UTF-8'}" alt="Thumb" style="width: 45px; height: 35px; object-fit: cover; border-radius: 3px; border: 1px solid #ccc;" />
                                    </a>
                                {else}
                                    <span class="text-muted">—</span>
                                {/if}
                            </td>
                            <td>
                                <div>
                                    <strong>{$row.display_room_num|escape:'html':'UTF-8'}</strong>
                                    {if !empty($row.display_floor)}
                                        <span class="badge badge-default" style="font-size: 0.75em; font-weight: normal; margin-left: 4px;">
                                            {l s='Floor' mod='qlovisualinspection'} {$row.display_floor|escape:'html':'UTF-8'}
                                        </span>
                                    {/if}
                                </div>
                                {if !empty($row.display_room_type)}
                                    <div class="text-muted" style="max-width: 190px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: 0.85em; cursor: help;" title="{$row.full_room_title|escape:'html':'UTF-8'}">
                                        {$row.display_room_type|escape:'html':'UTF-8'}
                                    </div>
                                {/if}
                            </td>
                            <td>
                                {$row.item_title|escape:'html':'UTF-8'}
                            </td>
                            <td class="text-center">
                                {if $row.assessment == 'EVIDENCE_VALID'}
                                    <span class="badge badge-success">{l s='VALID' mod='qlovisualinspection'}</span>
                                {else}
                                    <span class="badge badge-danger">{l s='RETAKE' mod='qlovisualinspection'}</span>
                                {/if}
                            </td>
                            <td class="text-center">
                                <strong>{$row.luminance|string_format:"%.1f"}</strong>
                                <small class="text-muted">({$row.luminance_status|escape:'html':'UTF-8'})</small>
                            </td>
                            <td class="text-center">
                                <strong>{$row.sharpness_score|string_format:"%.1f"}</strong>
                                <small class="text-muted">({$row.sharpness_status|escape:'html':'UTF-8'})</small>
                            </td>
                            <td class="text-center">
                                {$row.width|intval} &times; {$row.height|intval} px
                            </td>
                            <td>
                                {if !empty($row.employee_name)}
                                    {$row.employee_name|escape:'html':'UTF-8'}
                                {else}
                                    <span class="text-muted">{l s='Supervisor' mod='qlovisualinspection'}</span>
                                {/if}
                            </td>
                            <td class="text-center">
                                {$row.date_add|date_format:"%d/%m/%Y %H:%M"}
                            </td>
                        </tr>
                    {/foreach}
                </tbody>
            </table>
        </div>
    {else}
        <div class="alert alert-info" style="margin: 15px;">
            <i class="icon-info-circle"></i> {l s='No inspection records saved yet. Submitted evaluations will appear here for management auditing.' mod='qlovisualinspection'}
        </div>
    {/if}
</div>
