#!/bin/sh
# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

set -eu

mkdir -p /usr/share/nginx/html/assets
cat > /usr/share/nginx/html/assets/env-config.json <<EOF
{"apiUrl": "${TALOS_API_URL:-http://localhost:8080}"}
EOF

exec nginx -g 'daemon off;'
