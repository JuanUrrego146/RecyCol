# RecyCol Aporta — despliegue

**Ya está desplegado y funcionando.** Este documento sirve para entender qué hay
montado, cómo publicar cambios y cómo rehacerlo desde cero si hiciera falta.

## Dónde vive

| | |
|---|---|
| **Aplicación** | https://func-recycol-aporta-w94b924.azurewebsites.net |
| Grupo de recursos | `rg-recycol-aporta` (región `eastus`) |
| Suscripción | Azure for Students · `unimilitar.edu.co` |
| Coste | **Por debajo de 1 USD/mes**, con alerta de presupuesto en 5 USD |

Dos recursos y nada más:

- **`strecycolaporta94b924`** — cuenta de almacenamiento. Guarda las fotos en
  Blob Storage y los metadatos en Table Storage.
- **`func-recycol-aporta-w94b924`** — aplicación de funciones. Sirve la web
  **y** la API desde el mismo origen.

## Por qué no es lo que decía la propuesta

Tres cosas se rompieron contra los límites de la suscripción de estudiante, y las
tres tienen su rastro en el código:

**Cosmos DB no se puede crear.** Ni con capa gratuita, ni sin servidor, ni
aprovisionada, y en las cuatro regiones que la política permite. Responde
`ServiceUnavailable` diciendo «alta demanda», pero es un tope de la suscripción;
levantarlo exige una solicitud a Microsoft que tarda días. Los metadatos viven en
**Table Storage**, en la cuenta que ya guarda las fotos. Resultó encajar mejor:
su clave de partición es literalmente `contributorId`, que es la unidad que exige
`CONTEXTO.md` §10, y cuesta menos.

**Static Web Apps tampoco.** Solo existe en `centralus, eastus2, westus2,
westeurope, eastasia`; la política permite `southcentralus, chilecentral,
canadacentral, eastus, northcentralus`. **La intersección es vacía.** En su lugar
hay una aplicación de funciones que sirve también los archivos de la web
(`api/src/functions/site.ts`), con una ventaja de propina: un único origen para
la página, `/api/*` y `/.auth/*`, así que la sesión fluye sin CORS.

**El plan de consumo Linux no arranca en `eastus`.** Se creó sin errores, quedó
en «Running» y devolvió 503 indefinidamente, también su sitio de despliegue. El
plan **Windows** funcionó a la primera en la misma región. Por eso el nombre
lleva `w`.

## Publicar un cambio

Automático: cada vez que algo de `dataApp/` llega a `main`, el workflow
**DataApp** prueba, empaqueta y publica. Se ve en la pestaña Actions.

A mano, desde la raíz del repositorio:

```bash
CONTACT_EMAIL="juandavidurregofonseca677@gmail.com" bash dataApp/infra/package.sh
az functionapp deployment source config-zip \
  --name func-recycol-aporta-w94b924 --resource-group rg-recycol-aporta \
  --src dataApp/.package/recycol-aporta.zip
```

El paquete lleva dentro la web construida (`www/`) y la API compilada.

## Quién puede moderar

La lista está en la configuración de la aplicación, no en el código, y se cambia
sin volver a desplegar:

```bash
az functionapp config appsettings set \
  --name func-recycol-aporta-w94b924 --resource-group rg-recycol-aporta \
  --settings "ADMIN_EMAILS=uno@ejemplo.com,otro@ejemplo.com"
```

Ahora mismo: `juandavidurregofonseca677@gmail.com` y
`est.juan.durrego@unimilitar.edu.co`.

Sin estar en esa lista, `/revisar`, el informe académico y la exportación
responden 403 **a todo el mundo**. Es a propósito: la moderación es lo que hace
asumible que el enlace sea público.

## Cómo entra la gente

**Cuenta Microsoft**, y nada más que configurar. Cubre las cuentas
`@unimilitar.edu.co` —que además **acreditan** la pertenencia a la universidad,
porque la aplicación comprueba el dominio— y cualquier cuenta personal de
Microsoft, incluidas Outlook y Hotmail.

Quien no tenga ninguna **aporta igual, de forma anónima**. Es el camino por
defecto; simplemente no sale en el informe del profesor.

Nunca se escribe una contraseña en esta aplicación: la resuelve la página de
Microsoft y aquí solo llegan el nombre y el correo.

## Sacar el informe para un profesor

Con sesión de administrador abierta, en el navegador:

```
https://func-recycol-aporta-w94b924.azurewebsites.net/api/report/academic?format=csv
```

Filtros opcionales: `&professor=Ana%20Ríos`, `&group=B`, `&course=Cálculo%201`.
No hace falta escribirlo igual que ellos: se comparan ignorando tildes y
mayúsculas.

Las columnas son **fotos aprobadas**, **objetos distintos** y **materiales
distintos**, a propósito. Si se contaran envíos, treinta fotos de la misma lata
valdrían treinta; contando objetos valen una. El profesor pone la nota, nosotros
no inventamos una.

## Rehacerlo desde cero

```bash
bash dataApp/infra/provision.sh    # crea todo y configura la autenticación
CONTACT_EMAIL="…" bash dataApp/infra/package.sh
az functionapp deployment source config-zip --name … --src …
```

El script es idempotente y pide confirmación antes de crear nada.

## Trabajar en local

```bash
npm ci --prefix dataApp/web
npm run dev --prefix dataApp/web    # http://localhost:5173
```

La cámara funciona en `localhost` aunque no sea HTTPS. Sin API levantada la
pantalla de inicio avisa de que no puede consultar el avance, pero **se puede
aportar igual**: las fotos se guardan en el navegador y esperan. Es el mismo
camino que se recorre sin cobertura.

Para levantar también la API hace falta Azure Functions Core Tools y
`dataApp/api/local.settings.json` —ignorado por git, **no se sube nunca**—:

```json
{
  "IsEncrypted": false,
  "Values": {
    "FUNCTIONS_WORKER_RUNTIME": "node",
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "STORAGE_CONNECTION_STRING": "…",
    "ALLOW_LOCAL_ADMIN": "true"
  }
}
```

`ALLOW_LOCAL_ADMIN` abre la moderación sin sesión, **solo en local**. En Azure no
se define nunca: si estuviera puesta, la moderación y la exportación quedarían
abiertas a cualquiera.

## Qué cuesta

| Servicio | Coste real |
|---|---|
| Aplicación de funciones, plan de consumo | **0 USD** — el primer millón de ejecuciones al mes es gratis |
| Blob Storage | ~0,02 USD por GB/mes → **~0,10 USD** con 10 000 fotos |
| Table Storage | céntimos: los metadatos son texto |

El riesgo no está en este diseño, está en dejar encendido algo fuera de la capa
gratuita. Por eso hay alerta en 5 USD.

> ### ⚠️ La suscripción es Azure for Students
>
> Sus términos son para aprender. **No contamina los derechos del dataset** —esos
> los da el consentimiento, no el sitio donde vivan los bytes—, pero si Microsoft
> la suspendiera te quedarías sin servicio. Antes de cualquier lanzamiento
> comercial hay que mover esto a una suscripción de pago por uso: es volver a
> correr `provision.sh` allí y copiar el contenedor con `azcopy`. Con las capas
> gratuitas aplicando igual, el coste seguiría siendo el mismo.

**Para apagarlo todo:**

```bash
az group delete --name rg-recycol-aporta --yes
```

Eso **borra también las fotos aportadas**. Antes, exporta: ver
[INTEGRACION-ML.md](INTEGRACION-ML.md).
