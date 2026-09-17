#!/usr/bin/env bash
set -e

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Se executado a partir de scripts/, ajusta ROOT_DIR para a raiz
if [ "$(basename "$ROOT_DIR")" = "scripts" ]; then
  ROOT_DIR="$(cd "$ROOT_DIR/.." && pwd)"
fi

cd "$ROOT_DIR"
mkdir -p .logs

echo "=================================================="
echo "  Iniciando Ambiente Integrado QloApps + Sidecars"
echo "=================================================="

# 1. Ajustar permissoes da pasta de modulos
echo "[1/9] Ajustando permissoes e gerenciando container QloApps..."
podman unshare bash -c "chown -R 0:0 '$ROOT_DIR/modules' && chmod -R a+rwX '$ROOT_DIR/modules'" 2>/dev/null || true

# 2. Iniciar container (reutiliza existente para manter banco e configs intactos)
if podman container exists qloapps; then
  echo "Container 'qloapps' existente encontrado. Iniciando..."
  podman start qloapps >/dev/null 2>&1 || true
else
  echo "Criando novo container 'qloapps'..."
  podman run -d --name qloapps --network host \
    -v "$ROOT_DIR/modules:/home/qloapps/www/QloApps/modules:Z" \
    -v "$ROOT_DIR/qloinventoryaudit:/home/qloapps/www/QloApps/qloinventoryaudit:Z" \
    -v "$ROOT_DIR/classes:/home/qloapps/www/QloApps/classes:Z" \
    -v "$ROOT_DIR/controllers:/home/qloapps/www/QloApps/controllers:Z" \
    -v "$ROOT_DIR/override:/home/qloapps/www/QloApps/override:Z" \
    -v "$ROOT_DIR/themes:/home/qloapps/www/QloApps/themes:Z" \
    -v "$ROOT_DIR/Adapter:/home/qloapps/www/QloApps/Adapter:Z" \
    -v "$ROOT_DIR/Core:/home/qloapps/www/QloApps/Core:Z" \
    docker.io/webkul/qloapps_docker >.logs/podman.log 2>&1
fi

# Garante modulo qloinventoryaudit no container (caso nao tenha sido montado)
podman cp "$ROOT_DIR/qloinventoryaudit" qloapps:/home/qloapps/www/QloApps/qloinventoryaudit 2>/dev/null || true

# 2.1 Ajuste de porta para rootless podman (porta 8080) e tuning PHP
sleep 2
podman exec qloapps bash -c "
  # Idempotente: so substitui se ainda for porta 80 exata
  sed -i 's/^Listen 80$/Listen 8080/' /etc/apache2/ports.conf
  sed -i 's/<VirtualHost \*:80>/<VirtualHost \*:8080>/' /etc/apache2/sites-enabled/000-default.conf 2>/dev/null || true
  sed -i 's/<VirtualHost \*:80>/<VirtualHost \*:8080>/' /etc/apache2/sites-available/000-default.conf 2>/dev/null || true
  sed -i '/user-assistance\.js/d' /home/qloapps/www/QloApps/install/theme/views/header.phtml 2>/dev/null || true
" 2>/dev/null || true
podman exec qloapps bash -c 'echo -e "opcache.enable=1\nopcache.memory_consumption=256\nopcache.interned_strings_buffer=32\nopcache.max_accelerated_files=30000\nopcache.revalidate_freq=0\nrealpath_cache_size=16M\nrealpath_cache_ttl=7200\nmemory_limit=1024M" > /etc/php/8.4/mods-available/tuning.ini && phpenmod tuning' 2>/dev/null || true
podman exec qloapps bash -c 'source /etc/apache2/envvars && apache2 -k graceful 2>/dev/null || apache2 -k start' > /dev/null 2>&1 || true

# 2.2 Aguardar MySQL e configurar credenciais de conexao
echo "Aguardando inicializacao do MySQL no container..."
for _ in {1..30}; do
  if podman exec qloapps mysqladmin ping --silent 2>/dev/null || podman exec qloapps mysqladmin -uroot -proot ping --silent 2>/dev/null; then
    break
  fi
  sleep 1
done

podman exec qloapps chmod 755 /var/run/mysqld 2>/dev/null || true
podman exec qloapps bash -c "
mysql -uroot -proot -e \"
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root';
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY 'root';
ALTER USER 'root'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
CREATE DATABASE IF NOT EXISTS qloapps;
FLUSH PRIVILEGES;
\" 2>/dev/null || mysql -e \"
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root';
CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY 'root';
ALTER USER 'root'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
CREATE DATABASE IF NOT EXISTS qloapps;
FLUSH PRIVILEGES;
\" 2>/dev/null || true
" 2>/dev/null || true

# 2.3 Instalacao automatica se settings.inc.php nao existir
if ! podman exec qloapps [ -f /home/qloapps/www/QloApps/config/settings.inc.php ]; then
  echo "[*] QloApps nao configurado. Executando instalacao automatica via CLI..."
  podman cp "$ROOT_DIR/install" qloapps:/home/qloapps/www/QloApps/install
  podman exec qloapps php /home/qloapps/www/QloApps/install/index_cli.php \
    --newsletter=0 \
    --domain=localhost:8080 \
    --email=admin@qloapps.local \
    --password=adminadmin \
    --db_server=127.0.0.1 \
    --db_user=root \
    --db_password=root \
    --db_name=qloapps \
    --db_clear=1 \
    --prefix=qlo_ >.logs/install.log 2>&1 || true
  echo "[✓] Instalacao CLI concluida!"
fi

# 2.4 Limpar pasta install, caches e corrigir permissoes para o Apache (usuario qloapps)
podman exec qloapps rm -rf /home/qloapps/www/QloApps/install 2>/dev/null || true
podman exec qloapps chown -R qloapps:qloapps /home/qloapps/www/QloApps/cache /home/qloapps/www/QloApps/log /home/qloapps/www/QloApps/img /home/qloapps/www/QloApps/upload /home/qloapps/www/QloApps/download 2>/dev/null || true
podman exec qloapps chmod -R 777 /home/qloapps/www/QloApps/cache /home/qloapps/www/QloApps/log 2>/dev/null || true

# 2.5 Detecta pasta admin dinamicamente (nome muda a cada instalacao)
ADMIN_DIR=$(podman exec qloapps bash -c \
  "ls /home/qloapps/www/QloApps/ | grep '^admin' | head -1" 2>/dev/null || true)
if [ "$ADMIN_DIR" = "admin" ]; then
  ADMIN_DIR="admin$(openssl rand -hex 4)"
  podman exec qloapps bash -c "cd /home/qloapps/www/QloApps && mv admin '$ADMIN_DIR'"
  echo "  -> Pasta admin renomeada para $ADMIN_DIR"
fi

# 2.6 Configura redirecionamento automatico no Apache (/admin -> /$ADMIN_DIR/)
podman exec qloapps bash -c "
  sed -i '/RedirectMatch/d' /etc/apache2/sites-available/000-default.conf
  sed -i '/<VirtualHost/a \\\    RedirectMatch 302 ^/admin/?$ /$ADMIN_DIR/' /etc/apache2/sites-available/000-default.conf
  cp /etc/apache2/sites-available/000-default.conf /etc/apache2/sites-enabled/000-default.conf
  source /etc/apache2/envvars && apache2 -k graceful 2>/dev/null || true
" 2>/dev/null || true
echo "  -> Painel Admin: http://localhost:8080/admin (redireciona para /$ADMIN_DIR/)"

# 2.7 Configurar e instalar todos os modulos RFC no QloApps
echo "Configurando modulos RFC no QloApps..."
podman cp "$ROOT_DIR/scripts/setup_rfc_modules.php" qloapps:/home/qloapps/www/QloApps/setup_rfc_modules.php 2>/dev/null || true
podman exec qloapps php /home/qloapps/www/QloApps/setup_rfc_modules.php >.logs/setup-modules.log 2>&1 || true

# 3. Python Virtualenv compartilhado
if [ ! -d "$ROOT_DIR/.venv" ]; then
  echo "Configurando ambiente Python (.venv)..."
  python3 -m venv "$ROOT_DIR/.venv"
  "$ROOT_DIR/.venv/bin/pip" install --quiet --upgrade pip
  "$ROOT_DIR/.venv/bin/pip" install --quiet fastapi uvicorn pillow pydantic python-multipart httpx
fi
PY_BIN="$ROOT_DIR/.venv/bin/python"

# 4. Compilar C++ se necessario
echo "Verificando binarios C++..."
[ -f "$ROOT_DIR/assistant-service-cpp/assistant_service" ] || make -C "$ROOT_DIR/assistant-service-cpp" -j"$(nproc)" >.logs/build-assistant.log 2>&1
[ -f "$ROOT_DIR/overlap-service-cpp/overlap_service" ] || make -C "$ROOT_DIR/overlap-service-cpp" -j"$(nproc)" >.logs/build-overlap.log 2>&1
[ -f "$ROOT_DIR/search-service-cpp/search_service" ] || make -C "$ROOT_DIR/search-service-cpp" -j"$(nproc)" >.logs/build-search.log 2>&1

# 5. Configuracao de Java 21 para servicos Kotlin
ensure_jdk21() {
  local cur_ver=""
  if command -v java >/dev/null 2>&1; then
    cur_ver=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
  fi
  if [ -n "$cur_ver" ] && [ "$cur_ver" -ge 17 ] && [ "$cur_ver" -le 21 ]; then
    return 0
  fi

  local candidate
  candidate=$(find "$HOME/.gradle/jdks" "$ROOT_DIR/.jdk21" -maxdepth 3 -name "bin" -exec dirname {} \; 2>/dev/null | grep -E "21|temurin" | head -1)
  if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
  fi

  echo "[-] Java compativel (17-21) nao detectado. Baixando Adoptium Temurin 21..."
  mkdir -p "$ROOT_DIR/.jdk21"
  local tarball="/tmp/temurin21.tar.gz"
  curl -sL "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz" -o "$tarball"
  tar -xzf "$tarball" -C "$ROOT_DIR/.jdk21" --strip-components=1
  rm -f "$tarball"
  export JAVA_HOME="$ROOT_DIR/.jdk21"
  export PATH="$JAVA_HOME/bin:$PATH"
}
ensure_jdk21

# Remove duplicata com erro de digitacao em clone limpo
if [ -f "$ROOT_DIR/contact-health-service-kotlin/src/main/kotlin/com/hotel/contacthealth/Aplication.kt" ]; then
  mv "$ROOT_DIR/contact-health-service-kotlin/src/main/kotlin/com/hotel/contacthealth/Aplication.kt" \
     "$ROOT_DIR/contact-health-service-kotlin/src/main/kotlin/com/hotel/contacthealth/Aplication.kt.bak" 2>/dev/null || true
fi
chmod +x "$ROOT_DIR"/*-kotlin/gradlew 2>/dev/null || true
# 5. Inicializacao dos Sidecars em background
echo "[2/9] Subindo RFC-001 (assistant-service-cpp :8101)..."
"$ROOT_DIR/assistant-service-cpp/assistant_service" >.logs/rfc001-assistant.log 2>&1 &
PID_001=$!

echo "[3/9] Subindo RFC-002 (inspection-service-python :8102)..."
(cd "$ROOT_DIR/inspection-service-python" && PYTHONPATH=. "$PY_BIN" app/main.py) >.logs/rfc002-inspection.log 2>&1 &
PID_002=$!

echo "[4/9] Subindo RFC-003 (contact-health-service-kotlin :8103)..."
if [ ! -x "$ROOT_DIR/contact-health-service-kotlin/build/install/contact-health-service-kotlin/bin/contact-health-service-kotlin" ]; then
  (cd "$ROOT_DIR/contact-health-service-kotlin" && ./gradlew installDist -x test >.logs/build-contact.log 2>&1 || true)
fi
if [ -x "$ROOT_DIR/contact-health-service-kotlin/build/install/contact-health-service-kotlin/bin/contact-health-service-kotlin" ]; then
  "$ROOT_DIR/contact-health-service-kotlin/build/install/contact-health-service-kotlin/bin/contact-health-service-kotlin" >.logs/rfc003-contact.log 2>&1 &
else
  (cd "$ROOT_DIR/contact-health-service-kotlin" && ./gradlew run --no-daemon) >.logs/rfc003-contact.log 2>&1 &
fi
PID_003=$!

echo "[5/9] Subindo RFC-004 (location-service-kotlin :8104)..."
if [ ! -x "$ROOT_DIR/location-service-kotlin/build/install/location-service-kotlin/bin/location-service-kotlin" ]; then
  (cd "$ROOT_DIR/location-service-kotlin" && ./gradlew installDist -x test >.logs/build-location.log 2>&1 || true)
fi
if [ -x "$ROOT_DIR/location-service-kotlin/build/install/location-service-kotlin/bin/location-service-kotlin" ]; then
  "$ROOT_DIR/location-service-kotlin/build/install/location-service-kotlin/bin/location-service-kotlin" >.logs/rfc004-location.log 2>&1 &
else
  (cd "$ROOT_DIR/location-service-kotlin" && ./gradlew run --no-daemon) >.logs/rfc004-location.log 2>&1 &
fi
PID_004=$!

echo "[6/9] Subindo RFC-005 (policy-service-python :8105)..."
(cd "$ROOT_DIR/policy-service-python" && PYTHONPATH=. "$PY_BIN" app/main.py) >.logs/rfc005-policy.log 2>&1 &
PID_005=$!

echo "[7/9] Subindo RFC-006 (converter-service-kotlin :8106)..."
if [ ! -x "$ROOT_DIR/converter-service-kotlin/build/install/converter-service-kotlin/bin/converter-service-kotlin" ]; then
  (cd "$ROOT_DIR/converter-service-kotlin" && ./gradlew installDist -x test >.logs/build-converter.log 2>&1 || true)
fi
if [ -x "$ROOT_DIR/converter-service-kotlin/build/install/converter-service-kotlin/bin/converter-service-kotlin" ]; then
  "$ROOT_DIR/converter-service-kotlin/build/install/converter-service-kotlin/bin/converter-service-kotlin" >.logs/rfc006-converter.log 2>&1 &
else
  (cd "$ROOT_DIR/converter-service-kotlin" && ./gradlew run --no-daemon) >.logs/rfc006-converter.log 2>&1 &
fi
PID_006=$!
echo "[8/9] Subindo RFC-007 (overlap-service-cpp :8107)..."
"$ROOT_DIR/overlap-service-cpp/overlap_service" >.logs/rfc007-overlap.log 2>&1 &
PID_007=$!

echo "[9/9] Subindo RFC-008 (search-service-cpp :8108)..."
"$ROOT_DIR/search-service-cpp/search_service" >.logs/rfc008-search.log 2>&1 &
PID_008=$!
echo "$PID_001 $PID_002 $PID_003 $PID_004 $PID_005 $PID_006 $PID_007 $PID_008" >.logs/sidecars.pid

cleanup() {
  echo -e "\n[!] Encerrando todos os servicos..."
  kill $PID_001 $PID_002 $PID_003 $PID_004 $PID_005 $PID_006 $PID_007 $PID_008 2>/dev/null || true
  podman stop qloapps 2>/dev/null || true
  rm -f .logs/sidecars.pid
  echo "[✓] Ambiente encerrado."
  exit 0
}

trap cleanup SIGINT SIGTERM
echo "Verificando prontidao dos 8 servicos sidecar..."
for port in {8101..8108}; do
  READY=0
  for _ in {1..20}; do
    if curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:$port/healthz" 2>/dev/null | grep -q "200"; then
      READY=1
      break
    fi
    sleep 0.5
  done
  if [ "$READY" -eq 1 ]; then
    echo "  [✓] Porta $port: UP (healthz OK)"
  else
    echo "  [!] Porta $port: Inicializando (verifique log em .logs/)"
  fi
done


echo "=================================================="
echo "  Ambiente Operacional!"
echo "  - QloApps Web:      http://localhost:8080"
echo "  - Painel Admin:     http://localhost:8080/admin (ou http://localhost:8080/$ADMIN_DIR/)"
echo "  - RFC-001 (C++):    http://127.0.0.1:8101"
echo "  - RFC-002 (Python): http://127.0.0.1:8102"
echo "  - RFC-003 (Kotlin): http://127.0.0.1:8103"
echo "  - RFC-004 (Kotlin): http://127.0.0.1:8104"
echo "  - RFC-005 (Python): http://127.0.0.1:8105"
echo "  - RFC-006 (Kotlin): http://127.0.0.1:8106"
echo "  - RFC-007 (C++):    http://127.0.0.1:8107"
echo "  - RFC-008 (C++):    http://127.0.0.1:8108"
echo "  Logs unificados em: .logs/"
echo "  Pressione Ctrl+C para encerrar tudo."
echo "=================================================="

wait
