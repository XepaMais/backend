#!/usr/bin/env bash
# Recomeça a demonstração do Xepa+ (contas @demo.xepamais.com.br): apaga o que os visitantes
# mudaram e grava de novo os dados fictícios, com datas relativas a hoje.
# Os scripts vêm do repositório database: 05-limpar-demo.sql e 04-seed-demo.sql.
#
# Instalação na EC2 (uma vez), a partir do seu PC:
#   scp -i chave.pem deploy/resetar-demo.sh ../database/04-seed-demo.sql ../database/05-limpar-demo.sql ubuntu@SERVIDOR:/tmp/
#   ssh -i chave.pem ubuntu@SERVIDOR 'sudo mkdir -p /opt/xepamais/demo && sudo mv /tmp/resetar-demo.sh /tmp/0[45]-*.sql /opt/xepamais/demo/ \
#     && sudo chmod 755 /opt/xepamais/demo/resetar-demo.sh \
#     && echo "0 7 * * * root /opt/xepamais/demo/resetar-demo.sh >> /var/log/xepamais-demo.log 2>&1" | sudo tee /etc/cron.d/xepamais-demo'
# 07:00 UTC = 04:00 em São Paulo. Quem estiver na demonstração nessa hora precisa entrar de novo.
set -euo pipefail

PASTA="$(cd "$(dirname "$0")" && pwd)"
BANCO="${DB_NAME:-db_xepa}"

echo ">> $(date -Is) recomeçando a demonstração"
mysql --default-character-set=utf8mb4 "$BANCO" < "$PASTA/05-limpar-demo.sql"
mysql --default-character-set=utf8mb4 "$BANCO" < "$PASTA/04-seed-demo.sql"
echo ">> $(date -Is) pronto"
