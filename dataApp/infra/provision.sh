#!/usr/bin/env bash
#
# Aprovisionamiento de RecyCol Aporta en Azure.
#
# Crea todo lo necesario y no toca nada más. Es idempotente: volver a ejecutarlo
# sobre recursos ya creados no los rompe ni los duplica.
#
#   bash dataApp/infra/provision.sh
#
# Requisitos: Azure CLI instalado y `az login` hecho. El script no pide ni
# guarda contraseñas: usa la sesión que ya tiene la máquina.
#
# COSTE ESPERADO — por debajo de 1 USD/mes con 10 000 fotos:
#   · Static Web Apps  plan gratuito           0 USD  (100 GB de tráfico al mes)
#   · Cosmos DB        capa gratuita           0 USD  (1000 RU/s y 25 GB, permanente)
#   · Blob Storage     ~0,02 USD por GB/mes    ~0,10 USD con 10 000 fotos (~4 GB)
#
# El riesgo de coste no está en este diseño sino en dejar encendido algo fuera de
# la capa gratuita, así que el script deja puesta una alerta de presupuesto.

set -euo pipefail

# --- Parámetros. Se pueden sobreescribir por variable de entorno. -------------

LOCATION="${LOCATION:-eastus2}"
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-recycol-aporta}"
# Sufijo estable a partir del id de suscripción: dos ejecuciones dan el mismo
# nombre, así que el script se puede repetir sin crear cuentas huérfanas.
SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
SUFFIX="${SUFFIX:-$(printf '%s' "$SUBSCRIPTION_ID" | tr -d '-' | cut -c1-6)}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:-strecycolaporta${SUFFIX}}"
COSMOS_ACCOUNT="${COSMOS_ACCOUNT:-cosmos-recycol-aporta-${SUFFIX}}"
STATIC_WEB_APP="${STATIC_WEB_APP:-swa-recycol-aporta}"
DATABASE_NAME="${DATABASE_NAME:-recycol}"
CONTAINER_NAME="${CONTAINER_NAME:-captures}"
BUDGET_AMOUNT="${BUDGET_AMOUNT:-5}"

echo "Suscripción : $(az account show --query name -o tsv)"
echo "Región      : ${LOCATION}"
echo "Grupo       : ${RESOURCE_GROUP}"
echo "Storage     : ${STORAGE_ACCOUNT}"
echo "Cosmos      : ${COSMOS_ACCOUNT}"
echo "Static Web  : ${STATIC_WEB_APP}"
echo
read -r -p "¿Crear estos recursos? Coste esperado por debajo de 1 USD/mes. [s/N] " answer
[[ "${answer}" =~ ^[sSyY]$ ]] || { echo "Cancelado."; exit 1; }

# --- Grupo de recursos --------------------------------------------------------

az group create --name "${RESOURCE_GROUP}" --location "${LOCATION}" --output none
echo "✓ Grupo de recursos"

# --- Almacenamiento de imágenes ----------------------------------------------
#
# Standard_LRS: tres copias dentro del mismo centro de datos. Para un dataset que
# además se sincroniza a disco local con azcopy, replicar entre regiones sería
# pagar por una garantía que no hace falta.

az storage account create \
  --name "${STORAGE_ACCOUNT}" \
  --resource-group "${RESOURCE_GROUP}" \
  --location "${LOCATION}" \
  --sku Standard_LRS \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --allow-blob-public-access false \
  --output none
echo "✓ Cuenta de almacenamiento"

STORAGE_CONNECTION_STRING="$(az storage account show-connection-string \
  --name "${STORAGE_ACCOUNT}" --resource-group "${RESOURCE_GROUP}" \
  --query connectionString -o tsv)"

# Contenedor privado: los blobs solo se leen con una firma temporal emitida por
# la API. `--allow-blob-public-access false` de arriba lo hace imposible de abrir
# por accidente desde el portal.
az storage container create \
  --name "${CONTAINER_NAME}" \
  --account-name "${STORAGE_ACCOUNT}" \
  --connection-string "${STORAGE_CONNECTION_STRING}" \
  --public-access off \
  --output none
echo "✓ Contenedor privado ${CONTAINER_NAME}"

# --- Cosmos DB ----------------------------------------------------------------
#
# La capa gratuita solo admite UNA cuenta por suscripción. Si ya está en uso, se
# crea sin ella y se avisa: con este volumen el modo servidor sin servidor cuesta
# céntimos, pero conviene saberlo antes de la factura.

if az cosmosdb show --name "${COSMOS_ACCOUNT}" --resource-group "${RESOURCE_GROUP}" &>/dev/null; then
  echo "✓ Cuenta de Cosmos ya existente"
elif az cosmosdb create \
  --name "${COSMOS_ACCOUNT}" \
  --resource-group "${RESOURCE_GROUP}" \
  --locations regionName="${LOCATION}" failoverPriority=0 isZoneRedundant=False \
  --enable-free-tier true \
  --default-consistency-level Session \
  --output none 2>/dev/null; then
  echo "✓ Cuenta de Cosmos en capa gratuita"
else
  echo "⚠ La capa gratuita de Cosmos ya está usada en esta suscripción."
  echo "  Se crea en modo sin servidor: se paga por petición, céntimos a este volumen."
  az cosmosdb create \
    --name "${COSMOS_ACCOUNT}" \
    --resource-group "${RESOURCE_GROUP}" \
    --locations regionName="${LOCATION}" failoverPriority=0 isZoneRedundant=False \
    --capabilities EnableServerless \
    --default-consistency-level Session \
    --output none
  echo "✓ Cuenta de Cosmos sin servidor"
fi

IS_SERVERLESS="$(az cosmosdb show --name "${COSMOS_ACCOUNT}" --resource-group "${RESOURCE_GROUP}" \
  --query "contains(to_string(capabilities), 'EnableServerless')" -o tsv)"

if [[ "${IS_SERVERLESS}" == "true" ]]; then
  az cosmosdb sql database create \
    --account-name "${COSMOS_ACCOUNT}" --resource-group "${RESOURCE_GROUP}" \
    --name "${DATABASE_NAME}" --output none 2>/dev/null || true
else
  # 1000 RU/s compartidas entre los tres contenedores: es justo lo que cubre la
  # capa gratuita, y de sobra para este volumen.
  az cosmosdb sql database create \
    --account-name "${COSMOS_ACCOUNT}" --resource-group "${RESOURCE_GROUP}" \
    --name "${DATABASE_NAME}" --throughput 1000 --output none 2>/dev/null || true
fi
echo "✓ Base de datos ${DATABASE_NAME}"

# Contenedores. La clave de partición de `captures` es `contributorId` porque es
# la unidad de partición que exige CONTEXTO.md §10: por aportante, no por imagen.
create_container() {
  az cosmosdb sql container create \
    --account-name "${COSMOS_ACCOUNT}" \
    --resource-group "${RESOURCE_GROUP}" \
    --database-name "${DATABASE_NAME}" \
    --name "$1" \
    --partition-key-path "$2" \
    --output none 2>/dev/null || true
  echo "✓ Contenedor $1 (partición $2)"
}

create_container captures /contributorId
create_container contributors /id
create_container stats /id

COSMOS_CONNECTION_STRING="$(az cosmosdb keys list \
  --name "${COSMOS_ACCOUNT}" --resource-group "${RESOURCE_GROUP}" \
  --type connection-strings --query "connectionStrings[0].connectionString" -o tsv)"

# --- Static Web App -----------------------------------------------------------
#
# El plan gratuito incluye funciones gestionadas, HTTPS, dominio propio y
# autenticación integrada. No admite identidad administrada, y por eso las
# credenciales viajan como configuración de aplicación y no como rol de Azure.

if ! az staticwebapp show --name "${STATIC_WEB_APP}" --resource-group "${RESOURCE_GROUP}" &>/dev/null; then
  az staticwebapp create \
    --name "${STATIC_WEB_APP}" \
    --resource-group "${RESOURCE_GROUP}" \
    --location "${LOCATION}" \
    --sku Free \
    --output none
fi
HOSTNAME="$(az staticwebapp show --name "${STATIC_WEB_APP}" --resource-group "${RESOURCE_GROUP}" \
  --query defaultHostname -o tsv)"
echo "✓ Static Web App  https://${HOSTNAME}"

# --- CORS del almacenamiento --------------------------------------------------
#
# Imprescindible: el navegador sube la foto directamente al blob con una firma
# temporal, así que sin esta regla el PUT muere en la comprobación previa y
# **ninguna imagen llega**. Se limita al origen de la aplicación.

az storage cors clear --services b --connection-string "${STORAGE_CONNECTION_STRING}" --output none
az storage cors add \
  --services b \
  --methods PUT OPTIONS \
  --origins "https://${HOSTNAME}" \
  --allowed-headers "x-ms-blob-type" "content-type" \
  --exposed-headers "" \
  --max-age 3600 \
  --connection-string "${STORAGE_CONNECTION_STRING}" \
  --output none
echo "✓ CORS del almacenamiento restringido a https://${HOSTNAME}"

# --- Configuración de la aplicación ------------------------------------------
#
# Aquí es donde viven las credenciales. Nunca en el repositorio.

az staticwebapp appsettings set \
  --name "${STATIC_WEB_APP}" \
  --resource-group "${RESOURCE_GROUP}" \
  --setting-names \
    "COSMOS_CONNECTION_STRING=${COSMOS_CONNECTION_STRING}" \
    "STORAGE_CONNECTION_STRING=${STORAGE_CONNECTION_STRING}" \
    "COSMOS_DATABASE=${DATABASE_NAME}" \
    "STORAGE_CONTAINER=${CONTAINER_NAME}" \
    "ADMIN_ROLE=administrador" \
  --output none
echo "✓ Configuración cargada"

# --- Alerta de presupuesto ----------------------------------------------------

if az consumption budget create \
  --budget-name "recycol-aporta" \
  --amount "${BUDGET_AMOUNT}" \
  --category Cost \
  --time-grain Monthly \
  --start-date "$(date -u +%Y-%m-01)" \
  --end-date "$(date -u -d '+3 years' +%Y-%m-01 2>/dev/null || date -u -v+3y +%Y-%m-01)" \
  --resource-group "${RESOURCE_GROUP}" \
  --output none 2>/dev/null; then
  echo "✓ Alerta de presupuesto en ${BUDGET_AMOUNT} USD/mes"
else
  echo "⚠ No se pudo crear la alerta de presupuesto (permisos o suscripción)."
  echo "  Créala a mano: portal → Administración de costos → Presupuestos."
fi

# --- Token de despliegue ------------------------------------------------------

DEPLOYMENT_TOKEN="$(az staticwebapp secrets list \
  --name "${STATIC_WEB_APP}" --resource-group "${RESOURCE_GROUP}" \
  --query "properties.apiKey" -o tsv)"

cat <<RESUMEN

──────────────────────────────────────────────────────────────
Listo. La aplicación vivirá en:

    https://${HOSTNAME}

Falta un paso, y hay que darlo desde esta misma terminal para que
el token no pase por ningún sitio intermedio:

    gh secret set AZURE_STATIC_WEB_APPS_API_TOKEN \\
      --repo JuanUrrego146/RecyCol \\
      --body "${DEPLOYMENT_TOKEN}"

Y otro que se hace en el portal, una sola vez:

    Static Web App → Configuración → Administración de roles →
    Invitar usuario → GitHub → tu usuario → rol "administrador"

Sin ese rol, /revisar y la exportación quedan cerradas para todo el
mundo, incluido tú.
──────────────────────────────────────────────────────────────
RESUMEN
