{*
* View Front-Office: Tela de Compartilhamento de Chegada do Hóspede
* Modulo: qloarrivalsharing (RFC-004)
*}

{capture name=path}
    {l s='Aviso de Chegada ao Hotel' mod='qloarrivalsharing'}
{/capture}

<div class="row" style="margin-top: 20px; margin-bottom: 40px;">
    <div class="col-xs-12 col-sm-8 col-sm-offset-2 col-md-6 col-md-offset-3">
        
        {if $hasError}
            <div class="panel panel-default" style="box-shadow: 0 4px 15px rgba(0,0,0,0.08); border-radius: 8px; overflow: hidden;">
                <div class="panel-body text-center" style="padding: 40px 20px;">
                    <div style="font-size: 48px; color: #f0ad4e; margin-bottom: 15px;">
                        <i class="icon-warning-sign"></i>
                    </div>
                    <h3 style="margin-top: 0; color: #333;">{l s='Acesso Não Disponível' mod='qloarrivalsharing'}</h3>
                    <p class="text-muted" style="font-size: 15px; margin-bottom: 25px;">
                        {$errorMessage|escape:'html':'UTF-8'}
                    </p>
                    <p class="text-muted" style="font-size: 13px;">
                        {l s='Por favor, utilize o link oficial enviado em sua confirmação de reserva ou entre em contato diretamente com a recepção do hotel.' mod='qloarrivalsharing'}
                    </p>
                    <a href="{$base_dir|escape:'html':'UTF-8'}" class="btn btn-default" style="margin-top: 15px;">
                        <i class="icon-home"></i> {l s='Página Inicial' mod='qloarrivalsharing'}
                    </a>
                </div>
            </div>
        {else}
            <div class="panel panel-default" style="box-shadow: 0 4px 20px rgba(0,0,0,0.08); border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden;">
                
                <!-- Cabeçalho do Hotel e Identificação -->
                <div class="panel-heading" style="background: #2b3a4a; color: #ffffff; padding: 20px; text-align: center; border: none;">
                    <div style="font-size: 28px; margin-bottom: 6px;">
                        <i class="icon-building"></i>
                    </div>
                    <h2 style="margin: 0; font-size: 20px; font-weight: bold; color: #fff;">
                        {$booking.hotel_name|escape:'html':'UTF-8'}
                    </h2>
                    <span style="font-size: 12px; color: #cbd5e1; text-transform: uppercase; letter-spacing: 1px;">
                        {l s='Monitoramento de Chegada e Traslado' mod='qloarrivalsharing'}
                    </span>
                </div>

                <div class="panel-body" style="padding: 25px 20px;">
                    
                    <!-- Saudação e Resumo da Reserva (PII Protegido) -->
                    <div class="text-center" style="margin-bottom: 20px;">
                        <h3 style="margin-top: 0; font-size: 18px; color: #1e293b;">
                            {l s='Olá' mod='qloarrivalsharing'}, <strong>{$booking.guest_display_name|escape:'html':'UTF-8'}</strong>!
                        </h3>
                        <p class="text-muted" style="font-size: 14px; margin-bottom: 10px;">
                            {l s='Avise a nossa recepção quando estiver se aproximando do hotel para agilizarmos a preparação das suas boas-vindas.' mod='qloarrivalsharing'}
                        </p>
                        
                        <div style="display: inline-block; background: #f8fafc; border: 1px solid #e2e8f0; padding: 8px 16px; border-radius: 20px; font-size: 12px; color: #475569;">
                            <span><i class="icon-bookmark text-primary"></i> {l s='Reserva:' mod='qloarrivalsharing'} <strong>#{$booking.order_reference|escape:'html':'UTF-8'}</strong></span>
                            <span style="margin: 0 8px; color: #cbd5e1;">&bull;</span>
                            <span><i class="icon-clock-o text-muted"></i> {l s='Check-in:' mod='qloarrivalsharing'} <strong>{if !empty($booking.check_in_time)}{$booking.check_in_time|escape:'html':'UTF-8'}{else}14:00{/if}</strong></span>
                        </div>
                    </div>

                    <hr style="margin: 20px 0; border-color: #f1f5f9;" />

                    <!-- Termo de Consentimento e Privacidade (LGPD) -->
                    <div style="background: #fdfefe; border: 1px solid #e6ebf1; border-radius: 6px; padding: 14px 15px; margin-bottom: 20px;">
                        <div class="checkbox" style="margin: 0;">
                            <label style="font-size: 13px; color: #334155; font-weight: normal; cursor: pointer;">
                                <input type="checkbox" id="checkGpsConsent" style="margin-top: 2px;" />
                                <strong>{l s='Autorizo a leitura pontual da minha localização geográfica' mod='qloarrivalsharing'}</strong>
                            </label>
                        </div>
                        <div style="font-size: 11px; color: #64748b; margin-top: 6px; line-height: 1.4;">
                            <i class="icon-shield text-success"></i> 
                            {l s='Privacidade e Segurança: As coordenadas são capturadas uma única vez ao clicar. Não realizamos rastreamento contínuo em segundo plano.' mod='qloarrivalsharing'}
                        </div>
                    </div>

                    <!-- Botão de Ação Principal -->
                    <div style="margin-bottom: 15px;">
                        <button type="button" id="btnSendArrival" class="btn btn-primary btn-lg btn-block" disabled="disabled" style="padding: 14px 20px; font-size: 16px; font-weight: bold; border-radius: 6px; transition: all 0.2s ease;">
                            <i class="icon-location-arrow"></i> {l s='Estou Chegando!' mod='qloarrivalsharing'}
                        </button>
                    </div>

                    <!-- Box de Carregamento e Notificações Dinâmicas -->
                    <div id="guestLoading" style="display: none; padding: 12px; margin-top: 15px; border-radius: 6px; background: #f0f9ff; color: #0369a1; border: 1px solid #bae6fd; text-align: center; font-size: 14px;">
                        <i class="icon-spinner icon-spin"></i> {l s='Obtendo sua localização via GPS...' mod='qloarrivalsharing'}
                    </div>

                    <div id="guestAlertSuccess" style="display: none; padding: 15px; margin-top: 15px; border-radius: 6px; background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0;">
                        <div style="display: flex; align-items: flex-start; gap: 10px;">
                            <i class="icon-check-circle" style="font-size: 22px; margin-top: 2px;"></i>
                            <div>
                                <strong style="font-size: 15px; display: block; margin-bottom: 3px;">
                                    {l s='Recepção Notificada!' mod='qloarrivalsharing'}
                                </strong>
                                <span id="guestSuccessMessage" style="font-size: 13px;"></span>
                            </div>
                        </div>
                    </div>

                    <div id="guestAlertInfo" style="display: none; padding: 15px; margin-top: 15px; border-radius: 6px; background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe;">
                        <div style="display: flex; align-items: flex-start; gap: 10px;">
                            <i class="icon-road" style="font-size: 22px; margin-top: 2px;"></i>
                            <div>
                                <strong style="font-size: 15px; display: block; margin-bottom: 3px;">
                                    {l s='Sinal Recebido!' mod='qloarrivalsharing'}
                                </strong>
                                <span id="guestInfoMessage" style="font-size: 13px;"></span>
                            </div>
                        </div>
                    </div>

                    <div id="guestAlertError" style="display: none; padding: 15px; margin-top: 15px; border-radius: 6px; background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca;">
                        <div style="display: flex; align-items: flex-start; gap: 10px;">
                            <i class="icon-exclamation-triangle" style="font-size: 20px; margin-top: 2px;"></i>
                            <div>
                                <strong style="font-size: 14px; display: block; margin-bottom: 3px;">
                                    {l s='Atenção' mod='qloarrivalsharing'}
                                </strong>
                                <span id="guestErrorMessage" style="font-size: 13px;"></span>
                            </div>
                        </div>
                    </div>

                </div>

                <div class="panel-footer" style="background: #f8fafc; border-top: 1px solid #f1f5f9; padding: 12px 20px; text-align: center; font-size: 11px; color: #94a3b8;">
                    {l s='QloApps Traslado & Geofencing System &bull; Comunicação Criptografada' mod='qloarrivalsharing'}
                </div>
            </div>
        {/if}

    </div>
</div>

{if !$hasError}
<script type="text/javascript">
(function() {
    var checkConsent = document.getElementById('checkGpsConsent');
    var btnSend = document.getElementById('btnSendArrival');
    var boxLoading = document.getElementById('guestLoading');
    var boxSuccess = document.getElementById('guestAlertSuccess');
    var boxInfo = document.getElementById('guestAlertInfo');
    var boxError = document.getElementById('guestAlertError');
    var txtSuccess = document.getElementById('guestSuccessMessage');
    var txtInfo = document.getElementById('guestInfoMessage');
    var txtError = document.getElementById('guestErrorMessage');

    var ajaxEndpoint = '{$ajaxUrl|escape:'javascript'}';
    var idOrder = '{$idOrder|escape:'javascript'}';
    var orderToken = '{$token|escape:'javascript'}';

    function hideAllAlerts() {
        if (boxLoading) boxLoading.style.display = 'none';
        if (boxSuccess) boxSuccess.style.display = 'none';
        if (boxInfo) boxInfo.style.display = 'none';
        if (boxError) boxError.style.display = 'none';
    }

    if (checkConsent && btnSend) {
        checkConsent.addEventListener('change', function() {
            btnSend.disabled = !this.checked;
        });

        btnSend.addEventListener('click', function() {
            if (!checkConsent.checked) {
                return;
            }

            if (!navigator.geolocation) {
                hideAllAlerts();
                txtError.innerText = "{l s='Seu navegador não suporta geolocalização. Por favor, utilize um navegador moderno no celular.' mod='qloarrivalsharing' js=1}";
                boxError.style.display = 'block';
                return;
            }

            hideAllAlerts();
            boxLoading.style.display = 'block';
            btnSend.disabled = true;

            var geoOptions = {
                enableHighAccuracy: true,
                timeout: 10000,
                maximumAge: 0
            };

            navigator.geolocation.getCurrentPosition(
                function(pos) {
                    var lat = pos.coords.latitude;
                    var lng = pos.coords.longitude;

                    var formData = new FormData();
                    formData.append('id_order', idOrder);
                    formData.append('token', orderToken);
                    formData.append('lat', lat);
                    formData.append('lng', lng);
                    formData.append('ajax', '1');
                    formData.append('action', 'sendLocation');

                    fetch(ajaxEndpoint, {
                        method: 'POST',
                        body: formData
                    })
                    .then(function(res) {
                        return res.json();
                    })
                    .then(function(data) {
                        hideAllAlerts();
                        btnSend.disabled = false;

                        if (data.success) {
                            if (data.inside_geofence) {
                                txtSuccess.innerText = data.message;
                                boxSuccess.style.display = 'block';
                            } else {
                                txtInfo.innerText = data.message;
                                boxInfo.style.display = 'block';
                            }
                        } else {
                            txtError.innerText = data.message || "{l s='Ocorreu um erro ao processar sua localização.' mod='qloarrivalsharing' js=1}";
                            boxError.style.display = 'block';
                        }
                    })
                    .catch(function(err) {
                        hideAllAlerts();
                        btnSend.disabled = false;
                        txtError.innerText = "{l s='Falha de comunicação com o servidor. Por favor, tente novamente.' mod='qloarrivalsharing' js=1}";
                        boxError.style.display = 'block';
                    });
                },
                function(err) {
                    hideAllAlerts();
                    btnSend.disabled = false;

                    var errMsg = '';
                    switch(err.code) {
                        case 1: // PERMISSION_DENIED
                            errMsg = "{l s='Permissão de GPS negada. Para avisar a recepção, por favor habilite a permissão de localização do navegador.' mod='qloarrivalsharing' js=1}";
                            break;
                        case 2: // POSITION_UNAVAILABLE
                            errMsg = "{l s='Não foi possível obter a posição de GPS no momento. Verifique se a localização está ativada no seu aparelho.' mod='qloarrivalsharing' js=1}";
                            break;
                        case 3: // TIMEOUT
                            errMsg = "{l s='Tempo esgotado ao tentar capturar o sinal de GPS. Por favor, tente novamente em local aberto.' mod='qloarrivalsharing' js=1}";
                            break;
                        default:
                            errMsg = "{l s='Erro desconhecido ao capturar o GPS.' mod='qloarrivalsharing' js=1}";
                    }

                    txtError.innerText = errMsg;
                    boxError.style.display = 'block';
                },
                geoOptions
            );
        });
    }
})();
</script>
{/if}
