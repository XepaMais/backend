#!/usr/bin/env bash
# HTTPS na frente da API (Nginx + certificado gratuito Let's Encrypt).
#
# Antes de rodar:
#   1. No DNS do domínio, crie um registro A:  api.xepamais.com.br -> IP público da EC2
#   2. No Security Group da EC2, libere as portas 80 e 443 (a 80 é usada só para emitir/renovar o certificado)
#
# Rodar na EC2:   sudo bash setup-https.sh api.xepamais.com.br voce@exemplo.com
#
# Este script NÃO fecha a porta 8080: o site continua funcionando pelo caminho antigo enquanto isso.
# Depois de rodar (e testar https://api.xepamais.com.br/api/planos):
#   3. No vercel.json do front, troque o destino do /api para https://api.xepamais.com.br/api/:path*
#      e publique o front.
#   4. Só então feche o caminho antigo:
#        echo 'SERVER_ADDRESS=127.0.0.1' | sudo tee -a /etc/xepamais/api.env && sudo systemctl restart xepamais-api
#      e REMOVA a regra da porta 8080 no Security Group.
set -euo pipefail

DOMINIO="${1:?Informe o domínio, ex.: api.xepamais.com.br}"
EMAIL="${2:?Informe um e-mail para avisos do Lets Encrypt}"

if [ "$(id -u)" -ne 0 ]; then echo "Rode com sudo"; exit 1; fi

echo ">> Pacotes"
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y nginx certbot python3-certbot-nginx

echo ">> Site do Nginx para ${DOMINIO}"
cat > /etc/nginx/sites-available/xepamais-api <<EOF
# Sem limit_req por IP aqui: todo o tráfego chega pelos IPs da Vercel, então o limite valeria
# para todos os visitantes juntos. O limite de tentativas de login fica na própria API.
server {
    listen 80;
    server_name ${DOMINIO};

    client_max_body_size 8m;   # fotos enviadas em /api/imagens

    location /api/ {
        include /etc/nginx/snippets/xepamais-proxy.conf;
    }

    location / { return 404; }
}
EOF

cat > /etc/nginx/snippets/xepamais-proxy.conf <<'EOF'
proxy_pass http://127.0.0.1:8080;
proxy_http_version 1.1;
proxy_set_header Host $host;
# A Vercel já manda o IP real do visitante em X-Forwarded-For; o Nginx acrescenta o IP de quem conectou.
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_read_timeout 60s;
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
EOF

ln -sf /etc/nginx/sites-available/xepamais-api /etc/nginx/sites-enabled/xepamais-api
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo ">> Certificado HTTPS (renovação automática já vem configurada pelo certbot)"
certbot --nginx -d "${DOMINIO}" --non-interactive --agree-tos -m "${EMAIL}" --redirect

echo
echo "Pronto. Teste:  curl https://${DOMINIO}/api/planos"
echo "Depois: troque o destino no vercel.json, publique o front e só então feche a porta 8080 (veja o topo deste arquivo)."
