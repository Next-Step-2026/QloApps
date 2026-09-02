<div class="panel">
    <div class="panel-heading">
        <i class="icon-calendar"></i> Auditor de Integridade & Conflitos de Ocupação
    </div>

    {if $auditError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$auditError}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">Lote de Reservas (JSON):</label>
            <div class="col-lg-7">
                <textarea name="audit_batch_json" rows="6" class="form-control" placeholder='{"audit_batch_id": "lote-01", "reservations": [{"reservation_id": "RES-01", "room_id": "101", "check_in": "2026-09-01", "check_out": "2026-09-05", "guest_name": "Carlos"}]}'></textarea>
            </div>
        </div>

        <div class="form-group">
            <div class="col-lg-offset-3 col-lg-4">
                <button type="submit" name="submitRunAudit" class="btn btn-primary btn-block">
                    <i class="icon-search"></i> Executar Auditoria de Sobreposição
                </button>
            </div>
        </div>
    </form>

    {if $auditResult}
        <hr />
        <div class="well">
            <h4><i class="icon-bar-chart"></i> Relatório Consolidado de Auditoria:</h4>
            <p><strong>Total de Reservas Auditadas:</strong> {$auditResult.total_reservations_audited}</p>
            <p><strong>Total de Conflitos Encontrados:</strong> 
                {if $auditResult.total_conflicts_found > 0}
                    <span class="label label-danger">{$auditResult.total_conflicts_found} CONFLITO(S) DETECTADO(S)</span>
                {else}
                    <span class="label label-success">NENHUM CONFLITO DETECTADO</span>
                {/if}
            </p>

            {if $auditResult.total_conflicts_found > 0}
                <table class="table table-bordered table-striped mt-3">
                    <thead>
                        <tr>
                            <th>Quarto</th>
                            <th>Reserva A</th>
                            <th>Reserva B</th>
                            <th>Período do Conflito</th>
                            <th>Noites Sobrepostas</th>
                            <th>Severidade</th>
                        </tr>
                    </thead>
                    <tbody>
                        {foreach from=$auditResult.conflicts item=conflict}
                            <tr>
                                <td><strong>{$conflict.room_id}</strong></td>
                                <td><code>{$conflict.reservation_a_id}</code></td>
                                <td><code>{$conflict.reservation_b_id}</code></td>
                                <td>{$conflict.overlap_start} até {$conflict.overlap_end}</td>
                                <td>{$conflict.overlap_nights} noite(s)</td>
                                <td>
                                    {if $conflict.severity == 'HIGH'}
                                        <span class="label label-danger">ALTA</span>
                                    {else}
                                        <span class="label label-warning">MÉDIA</span>
                                    {/if}
                                </td>
                            </tr>
                        {/foreach}
                    </tbody>
                </table>
            {/if}
        </div>
    {/if}
</div>
