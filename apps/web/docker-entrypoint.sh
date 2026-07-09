#!/bin/sh
set -eu

mkdir -p /usr/share/nginx/html/assets
cat > /usr/share/nginx/html/assets/env-config.json <<EOF
{"apiUrl": "${TALOS_API_URL:-http://localhost:8080}"}
EOF

exec nginx -g 'daemon off;'
