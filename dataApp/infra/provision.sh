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
#   · Blob Storage     ~0,02 USD por GB/mes    ~0,10 USD con 10 000 fotos (~4 GB)
#   · Table Storage    ~0,05 USD por GB/mes    céntimos: los metadatos son texto
#
# No hay Cosmos DB, y no es por precio. La suscripción Azure for Students del
# proyecto rechaza crear cuentas de Cosmos en las cuatro regiones que su política
# permite, con capa gratuita, sin servidor y aprovisionada, con un
# «ServiceUnavailable» que dice alta demanda pero es un tope de la suscripción.
# Los metadatos viven en Table Storage, en esta misma cuenta de almacenamiento:
# su modelo de clave de partición es literalmente el del dominio. Ver la cabecera
# de dataApp/api/src/store.ts.
#
# El riesgo de coste no está en este diseño sino en dejar encendido algo fuera de
# la capa gratuita, así que el script deja puesta una alerta de presupuesto.

set -euo pipefail

# --- Parámetros. Se pueden sobreescribir por variable de entorno. -------------

# eastus (Virginia). Desde Colombia el tráfico enruta al noreste de Estados
# Unidos antes que a Sudamérica, así que da menos latencia real que São Paulo, y
# es de las pocas regiones que admite una suscripción Azure for Students, cuya
# política solo permite: southcentralus, chilecentral, canadacentral, eastus y
# northcentralus. Si la suscripción cambia, comprobar con:
#   az policy assignment list --query "[0].parameters.listOfAllowedLocations.value"
LOCATION="${LOCATION:-eastus}"
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-recycol-aporta}"
# Sufijo estable a partir del id de suscripción: dos ejecuciones dan el mismo
# nombre, así que el script se puede repetir sin crear cuentas huérfanas.
SUBSCRIPTION_ID="$(az account show --query id -o tsv)"
SUFFIX="${SUFFIX:-$(printf '%s' "$SUBSCRIPTION_ID" | tr -d '-' | cut -c1-6)}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:-strecycolaporta${SUFFIX}}"
FUNCTION_APP="${FUNCTION_APP:-func-recycol-aporta-w${SUFFIX}}"
CONTAINER_NAME="${CONTAINER_NAME:-captures}"
AAD_APP_NAME="${AAD_APP_NAME:-RecyCol Aporta}"
NODE_VERSION="${NODE_VERSION:-22}"
BUDGET_AMOUNT="${BUDGET_AMOUNT:-5}"

# Quién puede moderar, exportar y sacar el informe académico. Se comparan en
# minúsculas; separar con comas para varios.
ADMIN_EMAILS="${ADMIN_EMAILS:-juandavidurregofonseca677@gmail.com}"

echo "Suscripción : $(az account show --query name -o tsv)"
echo "Región      : ${LOCATION}"
echo "Grupo       : ${RESOURCE_GROUP}"
echo "Storage     : ${STORAGE_ACCOUNT}"
echo "Funciones   : ${FUNCTION_APP}"
echo "Admin       : ${ADMIN_EMAILS}"
echo
read -r -p "¿Crear estos recursos? Coste esperado por debajo de 1 USD/mes. [s/N] " answer
[[ "${answer}" =~ ^[sSyY]$ ]] || { echo "Cancelado."; exit 1; }

# --- Grupo de recursos --------------------------------------------------------

az group create --name "${RESOURCE_GROUP}" --location "${LOCATION}" --output none
echo "✓ Grupo de recursos"

# --- Proveedores de recursos --------------------------------------------------
#
# Una suscripción recién creada los trae sin registrar, y entonces crear una
# cuenta de almacenamiento falla con «SubscriptionNotFound» — un mensaje que
# manda a buscar el problema justo donde no está. Registrarlos es idempotente y
# tarda unos segundos si ya lo estaban.

for proveedor in Microsoft.Storage Microsoft.Web; do
  estado="$(az provider show --namespace "${proveedor}" --query registrationState -o tsv 2>/dev/null || echo NotRegistered)"
  if [[ "${estado}" != "Registered" ]]; then
    echo "  registrando ${proveedor}…"
    az provider register --namespace "${proveedor}" --wait --output none
  fi
done
echo "✓ Proveedores de recursos"

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

# --- Tablas de metadatos ------------------------------------------------------
#
# Las etiquetas, los aportantes y los contadores viven en Table Storage, en la
# misma cuenta que las fotos. Su clave de partición es `contributorId`, que es
# literalmente la unidad de partición que exige CONTEXTO.md §10.
#
# `pendingreview` es un índice de la cola de moderación: Table Storage no tiene
# índices secundarios, y sin él cada carga de la pantalla de revisión recorrería
# el dataset entero.
#
# `ipquota` lleva el tope diario por dirección de origen. No guarda direcciones:
# una fila por día y por resumen, con un contador y nada más (ver `ratelimit.ts`).
#
# La API también las crea sola al arrancar (`ensureTables` en store.ts); hacerlo
# aquí deja el recurso completo antes del primer despliegue.

for tabla in captures contributors counters pendingreview ipquota; do
  az storage table create \
    --name "${tabla}" \
    --account-name "${STORAGE_ACCOUNT}" \
    --connection-string "${STORAGE_CONNECTION_STRING}" \
    --output none 2>/dev/null || true
done
echo "✓ Tablas de metadatos"

# --- Aplicación de funciones ---------------------------------------------------
#
# Aquí vive todo: la API **y** la web, que sirve `src/functions/site.ts`. Un solo
# origen para la página, `/api/*` y `/.auth/*`, así que la sesión fluye sin CORS
# ni cookies entre dominios.
#
# No es Static Web Apps, que era el diseño original y habría sido más cómodo:
# solo existe en centralus, eastus2, westus2, westeurope y eastasia, y la
# política de la suscripción del proyecto permite southcentralus, chilecentral,
# canadacentral, eastus y northcentralus. La intersección es vacía.
#
# **Plan de consumo Windows, no Linux.** El Linux se creó sin errores, quedó en
# «Running» y devolvió 503 indefinidamente, también su sitio de despliegue. El
# Windows funcionó a la primera en la misma región.

if ! az functionapp show --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" &>/dev/null; then
  az functionapp create \
    --name "${FUNCTION_APP}" \
    --resource-group "${RESOURCE_GROUP}" \
    --storage-account "${STORAGE_ACCOUNT}" \
    --consumption-plan-location "${LOCATION}" \
    --runtime node \
    --runtime-version "${NODE_VERSION}" \
    --functions-version 4 \
    --output none
fi
HOSTNAME="$(az functionapp show --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
  --query defaultHostName -o tsv)"
echo "✓ Aplicación de funciones  https://${HOSTNAME}"

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
# Aquí viven las credenciales y la lista de administración. Nunca en el
# repositorio.

az functionapp config appsettings set \
  --name "${FUNCTION_APP}" \
  --resource-group "${RESOURCE_GROUP}" \
  --settings \
    "STORAGE_CONNECTION_STRING=${STORAGE_CONNECTION_STRING}" \
    "STORAGE_CONTAINER=${CONTAINER_NAME}" \
    "ADMIN_EMAILS=${ADMIN_EMAILS}" \
  --output none
echo "✓ Configuración cargada"

# --- Autenticación ------------------------------------------------------------
#
# Registro de aplicación en el directorio + App Service Authentication. La
# plataforma resuelve el inicio de sesión y reenvía la identidad ya validada;
# aquí no se guarda ni se ve ninguna contraseña.
#
# `AzureADandPersonalMicrosoftAccount` admite tanto las cuentas de la universidad
# —que además **acreditan** la pertenencia por el dominio del correo— como
# cualquier cuenta personal de Microsoft.

APP_ID="$(az ad app list --display-name "${AAD_APP_NAME}" --query "[0].appId" -o tsv 2>/dev/null || true)"
if [[ -z "${APP_ID}" ]]; then
  APP_ID="$(az ad app create \
    --display-name "${AAD_APP_NAME}" \
    --sign-in-audience AzureADandPersonalMicrosoftAccount \
    --web-redirect-uris "https://${HOSTNAME}/.auth/login/aad/callback" \
    --enable-id-token-issuance true \
    --query appId -o tsv)"
  az ad sp create --id "${APP_ID}" --output none 2>/dev/null || true
  echo "✓ Aplicación registrada en el directorio (${APP_ID})"

  # El secreto va directo a la configuración de la aplicación: no se imprime, no
  # se guarda en disco y no pasa por el historial de la terminal.
  SECRETO="$(az ad app credential reset --id "${APP_ID}" --append \
    --display-name easyauth --years 2 --query password -o tsv)"
  az functionapp config appsettings set \
    --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
    --settings "MICROSOFT_PROVIDER_AUTHENTICATION_SECRET=${SECRETO}" --output none
  unset SECRETO
  echo "✓ Secreto de cliente guardado en la configuración"
else
  echo "✓ Aplicación del directorio ya existente (${APP_ID})"
fi

# La extensión authV2 no viene de serie y sin ella los comandos fallan pidiendo
# confirmación por teclado, que en un script no llega nunca.
az extension add --name authV2 --allow-preview true --yes --only-show-errors 2>/dev/null || true

# Un Function App recién creado arranca en configuración de autenticación v1, y
# los comandos v2 se niegan a trabajar sobre ella. Hay que migrarla primero.
if [[ "$(az webapp auth config-version show --name "${FUNCTION_APP}" \
      --resource-group "${RESOURCE_GROUP}" --query configVersion -o tsv 2>/dev/null)" != "v2" ]]; then
  az webapp auth config-version upgrade --name "${FUNCTION_APP}" \
    --resource-group "${RESOURCE_GROUP}" --output none
fi

az webapp auth microsoft update \
  --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
  --client-id "${APP_ID}" \
  --client-secret-setting-name MICROSOFT_PROVIDER_AUTHENTICATION_SECRET \
  --issuer "https://login.microsoftonline.com/common/v2.0" \
  --yes --output none

# CRÍTICO: sin `AllowAnonymous` la plataforma exigiría iniciar sesión para ver la
# página, y aportar sin cuenta —que es el camino por defecto y una decisión de
# producto— dejaría de ser posible.
az webapp auth update \
  --name "${FUNCTION_APP}" --resource-group "${RESOURCE_GROUP}" \
  --enabled true --unauthenticated-client-action AllowAnonymous \
  --redirect-provider azureactivedirectory --output none
echo "✓ Autenticación configurada, acceso anónimo preservado"

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

cat <<RESUMEN

──────────────────────────────────────────────────────────────
Infraestructura lista. La aplicación vivirá en:

    https://${HOSTNAME}

Falta publicar el código:

    CONTACT_EMAIL="tu-correo@ejemplo.com" bash dataApp/infra/package.sh
    az functionapp deployment source config-zip \
      --name ${FUNCTION_APP} --resource-group ${RESOURCE_GROUP} \
      --src dataApp/.package/recycol-aporta.zip

Administración (moderación, informe académico y exportación) para:

    ${ADMIN_EMAILS}

Se cambia sin volver a desplegar, con `az functionapp config appsettings set`.
──────────────────────────────────────────────────────────────
RESUMEN
