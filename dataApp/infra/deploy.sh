#!/usr/bin/env bash
#
# Publica la aplicación y **comprueba que quedó viva**.
#
#   CONTACT_EMAIL="tu-correo@ejemplo.com" bash dataApp/infra/deploy.sh
#
# ## Por qué no basta con `config-zip`
#
# El despliegue por zip sobre un plan de consumo Windows descomprime en el
# recurso compartido de Azure Files que el host tiene montado como `wwwroot`. Si
# el host está sirviendo —y con el latido cada cinco minutos lo está siempre—
# puede quedarse con una vista a medias del contenido nuevo.
#
# El resultado observado el 08/08: `config-zip` respondió `"complete": true`, y a
# partir de ahí **todas las rutas devolvieron 500**, incluidos los archivos
# estáticos. El host ni siquiera podía enumerar sus funciones
# (`az functionapp function list` → «Bad Request»). Un reinicio lo arregló en
# veinte segundos. Nadie se habría enterado hasta abrir el enlace.
#
# Por eso aquí el despliegue no termina cuando Azure dice que terminó, sino
# cuando la aplicación responde 200 de verdad. Si no lo hace, este script falla
# con código distinto de cero para que se vea.

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-recycol-aporta}"
FUNCTION_APP="${FUNCTION_APP:-func-recycol-aporta-w94b924}"
PAQUETE="${RAIZ}/.package/recycol-aporta.zip"

command -v az >/dev/null 2>&1 || {
  echo "Falta el CLI de Azure. En Windows:"
  echo '  export PATH="/c/Program Files/Microsoft SDKs/Azure/CLI2/wbin:$PATH"'
  exit 1
}

if [[ ! -f "${PAQUETE}" || "${EMPAQUETAR:-1}" == "1" ]]; then
  bash "${RAIZ}/infra/package.sh"
fi

echo "→ Publicando en ${FUNCTION_APP}"
az functionapp deployment source config-zip \
  --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
  --src "${PAQUETE}" --output none

# Reinicio deliberado: es lo que evita quedarse con la vista a medias descrita
# arriba. Cuesta unos segundos y ahorra un sitio caído sin avisar.
echo "→ Reiniciando para que el host recoja el contenido nuevo"
az functionapp restart --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" --output none

BASE="https://$(az functionapp show --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
  --query defaultHostName -o tsv)"

echo "→ Comprobando que responde"
sano=0
for intento in $(seq 1 15); do
  sleep 12
  portada="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/" --max-time 60 || echo 000)"
  api="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/api/stats" --max-time 60 || echo 000)"
  echo "   intento ${intento}: portada ${portada} · api ${api}"
  if [[ "${portada}" == "200" && "${api}" == "200" ]]; then
    sano=1
    break
  fi
done

if [[ "${sano}" != "1" ]]; then
  echo
  echo "✗ El despliegue terminó pero la aplicación NO responde."
  echo "  Prueba a reiniciar a mano y vuelve a comprobar:"
  echo "    az functionapp restart --name ${FUNCTION_APP} --resource-group ${RESOURCE_GROUP}"
  exit 1
fi

# La lista de funciones es la comprobación que habría cazado el latido perdido
# cuando se quitó el paquete de extensiones: el despliegue fue verde y la función
# simplemente no estaba.
echo "→ Funciones registradas"
az functionapp function list --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
  --query "[].name" -o tsv 2>/dev/null | sed "s|${FUNCTION_APP}/|   |" | sort

if ! az functionapp function list --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
  --query "[].name" -o tsv 2>/dev/null | grep -q "keepWarm"; then
  echo
  echo "✗ Falta 'keepWarm'. Sin ese disparador la instancia se apaga y vuelven los"
  echo "  40 segundos de arranque en frío. Comprueba que host.json conserva el"
  echo "  extensionBundle: el disparador de temporizador lo aporta ese paquete."
  exit 1
fi

echo
echo "✓ Desplegado y verificado: ${BASE}"
