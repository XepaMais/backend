set -euo pipefail

FRONT_URL="${1:-http://localhost:5173}"
DB_NAME="db_xepa"
DB_USER="xepamais"

if [ "$(id -u)" -ne 0 ]; then echo "Rode com sudo"; exit 1; fi

echo ">> Swap de 2 GB (1 GB de RAM não basta para Java + MySQL)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo ">> Pacotes"
apt-get update -y
# default-jre-headless = Java 21 no Ubuntu 24.04 (roda o .jar compilado em Java 17)
DEBIAN_FRONTEND=noninteractive apt-get install -y default-jre-headless mysql-server

echo ">> MySQL enxuto para 1 GB de RAM"
cat > /etc/mysql/mysql.conf.d/zz-pouca-memoria.cnf <<'EOF'
[mysqld]
performance_schema = OFF
innodb_buffer_pool_size = 128M
max_connections = 30
EOF
systemctl restart mysql

echo ">> Banco e usuário"
mkdir -p /etc/xepamais
if [ ! -f /etc/xepamais/api.env ]; then
  DB_PASS="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)"
  mysql <<EOF
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
EOF
  cat > /etc/xepamais/api.env <<EOF
DB_URL=jdbc:mysql://localhost:3306/${DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Sao_Paulo
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASS}
SERVER_PORT=8080
APP_CORS_ALLOWED_ORIGINS=${FRONT_URL},http://localhost:5173
SHOW_SQL=false
JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m
EOF
  chmod 600 /etc/xepamais/api.env
fi

echo ">> Usuário de serviço e pasta da aplicação"
id xepamais >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin xepamais
mkdir -p /opt/xepamais
chown xepamais:xepamais /opt/xepamais

cat > /etc/systemd/system/xepamais-api.service <<'EOF'
[Unit]
Description=XepaMais API (Spring Boot)
After=network.target mysql.service
Requires=mysql.service

[Service]
User=xepamais
WorkingDirectory=/opt/xepamais
EnvironmentFile=/etc/xepamais/api.env
ExecStart=/usr/bin/java -jar /opt/xepamais/api.jar
Restart=always
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable xepamais-api

echo
echo "Pronto. Próximos passos:"
echo "  1. Importar as tabelas:  sudo mysql ${DB_NAME} < seu-script.sql"
echo "  2. Enviar o api.jar do seu PC com o deploy.ps1 (ele reinicia o serviço)"
echo "  3. Ver logs:  sudo journalctl -u xepamais-api -f"
