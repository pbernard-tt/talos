#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

# Renders talos-implementation-plan.md to the canonical PDF.
set -euo pipefail
cd "$(dirname "$0")"

npx -y marked --gfm -i talos-implementation-plan.md -o body.html

cat > full.html <<'EOF'
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  @page { size: letter; margin: 20mm 18mm; }
  body { font-family: 'DejaVu Serif', Georgia, serif; font-size: 10.5pt; line-height: 1.45; color: #1a1a1a; }
  h1 { color: #1f4e79; font-family: 'DejaVu Sans', Helvetica, sans-serif; font-size: 26pt; margin: 0 0 4pt 0; }
  h2 { color: #1f4e79; font-family: 'DejaVu Sans', Helvetica, sans-serif; font-size: 15pt; margin: 18pt 0 6pt 0; page-break-after: avoid; }
  h3 { color: #2e74b5; font-family: 'DejaVu Sans', Helvetica, sans-serif; font-size: 12pt; margin: 12pt 0 4pt 0; page-break-after: avoid; }
  h4 { color: #2e74b5; font-family: 'DejaVu Sans', Helvetica, sans-serif; font-size: 10.5pt; margin: 10pt 0 3pt 0; page-break-after: avoid; }
  p { margin: 5pt 0; }
  table { border-collapse: collapse; width: 100%; margin: 8pt 0; font-size: 9pt; page-break-inside: auto; }
  th { background: #1f4e79; color: #fff; text-align: left; font-family: 'DejaVu Sans', Helvetica, sans-serif; }
  th, td { border: 1px solid #444; padding: 4pt 6pt; vertical-align: top; }
  tr { page-break-inside: avoid; }
  code { font-family: 'DejaVu Sans Mono', Menlo, monospace; font-size: 8.5pt; background: #f2f5f8; padding: 0 2px; }
  pre { background: #f2f5f8; border: 1px solid #cdd7e1; padding: 8pt; font-size: 8pt; line-height: 1.35; overflow-x: hidden; white-space: pre-wrap; word-wrap: break-word; page-break-inside: auto; }
  pre code { background: none; font-size: 8pt; padding: 0; }
  blockquote { border: 1px solid #1f4e79; background: #eaf1f8; margin: 10pt 0; padding: 6pt 10pt; page-break-inside: avoid; }
  blockquote p { margin: 3pt 0; }
  blockquote strong:first-child { color: #1f4e79; font-family: 'DejaVu Sans', Helvetica, sans-serif; }
  ul, ol { margin: 5pt 0; padding-left: 20pt; }
  li { margin: 2pt 0; }
  a { color: #2e74b5; text-decoration: none; }
  .pagebreak { page-break-after: always; }
  .titlepage { text-align: left; }
  .titlepage h1 { font-size: 34pt; text-align: center; margin-top: 30pt; }
  .titlepage h2 { font-size: 15pt; text-align: center; color: #444; border: none; }
  .titlepage p em { display: block; text-align: center; }
  hr { border: none; border-top: 1px solid #ccc; margin: 12pt 0; }
</style>
</head>
<body>
EOF
cat body.html >> full.html
echo '</body></html>' >> full.html

google-chrome --headless --disable-gpu --no-sandbox --no-pdf-header-footer \
  --print-to-pdf="$(pwd)/talos-implementation-plan.pdf" "file://$(pwd)/full.html" 2>/dev/null

rm -f body.html full.html
echo "Generated: $(pwd)/talos-implementation-plan.pdf"
