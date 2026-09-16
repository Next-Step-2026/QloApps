#!/usr/bin/env bash

set -e

echo "=========================================="
echo "  QloApps Development Setup Script"
echo "=========================================="

CONFIG_FILE="config/defines.inc.php"

# 1. Ativar modo de depuração (_PS_MODE_DEV_ -> true)
if [ -f "$CONFIG_FILE" ]; then
    echo "[+] Ativando _PS_MODE_DEV_ em $CONFIG_FILE..."
    # Substitui define('_PS_MODE_DEV_', false) por true
    sed -i "s/define('_PS_MODE_DEV_', false);/define('_PS_MODE_DEV_', true);/g" "$CONFIG_FILE"
    echo "[✓] Modo de debug (Error Reporting) ativado."
else
    echo "[!] Arquivo $CONFIG_FILE não encontrado. Verifique se está na raiz do projeto."
    exit 1
fi

# 3. Limpar cache Smarty antigo
echo "[+] Limpando caches residuais do Smarty..."
rm -rf cache/smarty/compile/* cache/smarty/cache/* 2>/dev/null || true

echo "=========================================="
echo "  Configuração local concluída com sucesso!"
echo "=========================================="