#!/usr/bin/env bash
#
# Empaqueta la aplicación completa —web y API— en un zip listo para desplegar.
#
#   bash dataApp/infra/package.sh
#
# Produce `dataApp/.package/recycol-aporta.zip`.
#
# La web y la API viajan juntas en el mismo paquete a propósito: las sirve el
# mismo Function App, así que comparten origen y la sesión fluye sin CORS ni
# cookies entre dominios. `src/functions/site.ts` sirve `www/`.

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGING="${RAIZ}/.package/app"
ZIP="${RAIZ}/.package/recycol-aporta.zip"

CONTACT_EMAIL="${CONTACT_EMAIL:-}"

echo "→ Construyendo la web"
if [[ -z "${CONTACT_EMAIL}" ]]; then
  echo "  ⚠ CONTACT_EMAIL sin definir: la pantalla de consentimiento avisará de que falta."
fi
VITE_CONTACT_EMAIL="${CONTACT_EMAIL}" npm run build --prefix "${RAIZ}/web"

echo "→ Compilando la API"
npm run build --prefix "${RAIZ}/api"

echo "→ Preparando el paquete"
rm -rf "${RAIZ}/.package"
mkdir -p "${STAGING}"

cp "${RAIZ}/api/host.json" "${STAGING}/"
cp "${RAIZ}/api/package.json" "${STAGING}/"
cp "${RAIZ}/api/package-lock.json" "${STAGING}/"
cp -r "${RAIZ}/api/dist" "${STAGING}/dist"
cp -r "${RAIZ}/web/dist" "${STAGING}/www"

echo "→ Instalando dependencias de producción"
# Solo las de ejecución: vitest y typescript no pintan nada en el contenedor y
# multiplicarían el tamaño del paquete.
npm ci --omit=dev --prefix "${STAGING}" --no-audit --no-fund >/dev/null

echo "→ Podando lo que el runtime nunca abre"
# Más de la mitad del paquete eran mapas de código y declaraciones de tipos: peso
# que en un plan de consumo Windows se paga en cada arranque en frío, porque
# `wwwroot` vive en un recurso compartido por red y cada archivo cuesta viajes.
#
# Nada de esto lo lee Node en ejecución. Medido: 42 MB y 5.361 archivos antes,
# ~14 MB y ~1.700 después.
find "${STAGING}/node_modules" \
  \( -name '*.map' -o -name '*.d.ts' -o -name '*.md' -o -name '*.ts' \) \
  -type f -delete 2>/dev/null || true
# El mapa de la web pesa casi el triple que el propio bundle y solo sirve para
# depurar; se conserva en `web/dist`, fuera del paquete.
find "${STAGING}/www" -name '*.map' -type f -delete 2>/dev/null || true

echo "→ Comprimiendo"
if command -v powershell.exe >/dev/null 2>&1; then
  # En Windows, Compress-Archive evita depender de que haya `zip` en el PATH.
  powershell.exe -NoProfile -Command \
    "Compress-Archive -Path '$(cygpath -w "${STAGING}")\\*' -DestinationPath '$(cygpath -w "${ZIP}")' -Force" >/dev/null
else
  (cd "${STAGING}" && zip -qr "${ZIP}" .)
fi

TAMANO="$(du -h "${ZIP}" | cut -f1)"
echo "✓ ${ZIP} (${TAMANO})"
