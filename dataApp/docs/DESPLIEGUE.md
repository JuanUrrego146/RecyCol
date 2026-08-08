# Poner RecyCol Aporta en marcha

Guía para Juan. No hace falta saber de Azure: son seis pasos y el único que se
hace a mano en el portal es el último.

**Coste esperado: por debajo de 1 USD al mes** con 10 000 fotos. El detalle está
al final.

---

## 1 · Instalar el CLI de Azure

En PowerShell:

```powershell
winget install --id Microsoft.AzureCLI -e
```

**Cierra la terminal y ábrela de nuevo** al terminar; si no, el comando `az`
todavía no existe para esa ventana.

Comprueba que quedó bien:

```powershell
az version
```

## 2 · Entrar en tu cuenta

```powershell
az login
```

Se abre el navegador, entras con tu cuenta de Microsoft y listo. **No hace falta
que me pases ninguna clave ni token**: el CLI guarda la sesión en tu máquina y el
script trabaja con ella.

Comprueba que agarró la suscripción correcta:

```powershell
az account show --output table
```

Si tienes más de una:

```powershell
az account list --output table
az account set --subscription "<nombre de la que quieras>"
```

## 3 · Crear los recursos

Desde la raíz del repositorio, en **Git Bash** (no PowerShell — el script es de
bash):

```bash
bash dataApp/infra/provision.sh
```

Te enseña qué va a crear y te pide confirmación antes de tocar nada. Tarda unos
cinco minutos, casi todos esperando a Cosmos DB.

Al terminar imprime la dirección de la aplicación y los dos comandos de los pasos
siguientes, ya rellenos.

**Qué crea, y para qué sirve cada cosa:**

| Recurso | Para qué |
|---|---|
| Static Web App | Sirve la página y la API. Es lo que da la dirección `https://…` |
| Cuenta de almacenamiento | Guarda las fotos. Privada: solo se leen con permiso temporal |
| Cosmos DB | Guarda las etiquetas y los datos de cada foto |
| Alerta de presupuesto | Te avisa por correo si el gasto pasa de 5 USD al mes |

## 4 · Dar de alta el token de despliegue

El script imprime este comando ya relleno. Cópialo tal cual:

```bash
gh secret set AZURE_STATIC_WEB_APPS_API_TOKEN --repo JuanUrrego146/RecyCol --body "…"
```

Eso deja el token guardado en GitHub para que cada cambio en `dataApp/` se
publique solo. **El token no pasa por el chat ni queda en ningún archivo.**

## 5 · Poner el correo de contacto

El aviso de consentimiento promete que se pueden borrar los aportes, y para eso
hace falta una dirección donde pedirlo. Elige la que quieras usar de cara al
público:

```bash
gh variable set CONTACT_EMAIL --repo JuanUrrego146/RecyCol --body "tu-correo@ejemplo.com"
```

Mientras falte, la pantalla de consentimiento muestra un aviso. **Ponlo antes de
repartir el enlace.**

## 6 · Darte permiso de moderación

Este es el único paso en el portal, y hay que hacerlo una sola vez:

1. Entra en [portal.azure.com](https://portal.azure.com) y busca
   **swa-recycol-aporta**.
2. Menú lateral → **Configuración** → **Administración de roles**.
3. **Invitar usuario** → proveedor **Azure Active Directory** → tu correo → en el
   campo de roles escribe `administrador`.
4. Se genera un enlace de invitación: ábrelo tú mismo y acéptalo.

Sin ese rol, `/revisar`, el informe para profesores y la exportación están
cerrados **para todo el mundo, incluido tú**. Es a propósito: la moderación es lo
que hace asumible que el enlace sea público.

---

## Sobre las cuentas de quien aporta

**No hay nada que configurar.** El acceso con cuenta Microsoft y con GitHub viene
incluido en Static Web Apps y funciona en cuanto la aplicación está publicada. No
manejamos contraseñas: la persona entra en la página de su proveedor y nosotros
solo recibimos su nombre y su correo.

Entrar con un correo `@unimilitar.edu.co` **acredita** la pertenencia a la UMNG
porque la aplicación comprueba el dominio. Quien declare ser de la universidad
desde una cuenta personal aparece en el informe como «declarado», no verificado.

**Google cuesta 9 USD al mes.** Entra como proveedor OpenID personalizado y eso
exige el plan Standard. Está preparado en el código
(`web/src/data/session.ts`, `LOGIN_PROVIDERS`): cambiar `enabled` a `true`, subir
el plan y añadir el bloque `auth` a `staticwebapp.config.json`. Mientras tanto,
quien no tenga cuenta Microsoft puede aportar de forma anónima, que sigue siendo
el camino por defecto.

## Sacar el informe para un profesor

Con la sesión de administrador abierta, en el navegador:

```
https://<tu-host>/api/report/academic?format=csv
```

Descarga un CSV con una fila por estudiante. Para filtrar por profesor, grupo o
clase se añade a la dirección (`&professor=Ana%20Ríos`, `&group=B`,
`&course=C%C3%A1lculo%201`); no hace falta escribirlo igual que ellos, se
comparan ignorando tildes y mayúsculas.

Las columnas son **fotos aprobadas**, **objetos distintos** y **materiales
distintos**, a propósito. Si se contaran envíos, treinta fotos de la misma lata
valdrían treinta; contando objetos valen una. El profesor pone la nota, nosotros
no inventamos una.

---

## Publicar

A partir de aquí, cada vez que algo de `dataApp/` llegue a `main`, GitHub
Actions prueba, construye y publica solo. Se ve en la pestaña **Actions**,
workflow **DataApp**.

Para publicar a mano la primera vez, o si Actions falla:

```bash
npm ci --prefix dataApp/web
npm run build --prefix dataApp/web
npx @azure/static-web-apps-cli deploy dataApp/web/dist \
  --api-location dataApp/api \
  --deployment-token "<el token del paso 4>" \
  --env production
```

## Probar en el móvil

Abre la dirección en el navegador del teléfono. La cámara **solo funciona por
HTTPS**, que es lo que da Static Web Apps de serie.

Merece la pena instalarla como aplicación —«Añadir a pantalla de inicio»— porque
entonces abre a pantalla completa y se comporta como una app nativa.

---

## Trabajar en local

```bash
npm ci --prefix dataApp/web
npm run dev --prefix dataApp/web
```

Abre `http://localhost:5173`. La cámara funciona en `localhost` aunque no sea
HTTPS.

Sin la API levantada la pantalla de inicio avisa de que no puede consultar el
avance, pero **se puede aportar igual**: las fotos se guardan en el navegador y
esperan. Es el mismo camino que se recorre sin cobertura.

Para levantar también la API hace falta Azure Functions Core Tools y las
credenciales en `dataApp/api/local.settings.json` (ese archivo está ignorado por
git y **no debe subirse nunca**):

```json
{
  "IsEncrypted": false,
  "Values": {
    "FUNCTIONS_WORKER_RUNTIME": "node",
    "AzureWebJobsStorage": "",
    "COSMOS_CONNECTION_STRING": "…",
    "STORAGE_CONNECTION_STRING": "…",
    "ALLOW_LOCAL_ADMIN": "true"
  }
}
```

`ALLOW_LOCAL_ADMIN` abre la moderación sin sesión, **solo en local**. En Azure no
se define nunca: si estuviera puesta, `/api/review/*` y `/api/export/*` quedarían
abiertas a cualquiera.

---

## Qué cuesta esto de verdad

| Servicio | Capa gratuita | Coste real esperado |
|---|---|---|
| Static Web Apps | Plan Free: 100 GB de tráfico al mes, HTTPS y dominio propio | **0 USD** |
| Cosmos DB | 1000 RU/s y 25 GB, **permanente** (una cuenta por suscripción) | **0 USD** |
| Blob Storage | No tiene | ~0,02 USD por GB/mes → **~0,10 USD** con 10 000 fotos (unos 4 GB) |

**Total: menos de 1 USD al mes.**

El riesgo de coste no está en este diseño, está en dejar encendido algo fuera de
la capa gratuita. Por eso el script deja puesta la alerta en 5 USD. Si llega ese
correo, algo se salió del plan.

Si la capa gratuita de Cosmos ya estaba usada en tu suscripción, el script lo
detecta y crea la base en modo **sin servidor**: se paga por petición y a este
volumen son céntimos, pero conviene que lo sepas antes de ver la factura. El
script te lo dice por pantalla mientras corre.

**Para apagarlo todo** y dejar el gasto en cero:

```bash
az group delete --name rg-recycol-aporta --yes
```

Eso **borra también las fotos aportadas**. Antes, exporta (ver
[INTEGRACION-ML.md](INTEGRACION-ML.md)).
