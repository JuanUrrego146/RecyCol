/**
 * Servir la aplicación web desde el propio Function App.
 *
 * Static Web Apps hacía esto solo —CDN, cabeceras, reescritura a `index.html`— y
 * no está disponible en ninguna región que la suscripción del proyecto permita.
 * Aquí lo hace una función atrapatodo, y a cambio de escribirla se gana algo:
 * **un único origen** para la web, la API y `/.auth/*`, así que la sesión fluye
 * sin CORS ni cookies entre dominios.
 *
 * Las cabeceras de seguridad que antes declaraba `staticwebapp.config.json`
 * viven ahora aquí, que es el único sitio por el que pasa todo lo que se sirve.
 */

import { app, HttpRequest, HttpResponseInit } from "@azure/functions";
import { createReadStream, existsSync, statSync } from "node:fs";
import { extname, join, normalize, resolve, sep } from "node:path";

/**
 * Raíz de los archivos publicados. El empaquetado copia `web/dist` a `www/` en
 * la raíz de la aplicación.
 *
 * Se prueban varias rutas porque el directorio de trabajo de una función no está
 * garantizado y la profundidad del código compilado depende de `outDir`. Fallar
 * aquí significaría servir 404 en la página de inicio, así que vale la pena la
 * comprobación.
 */
const WEB_ROOT = [
  resolve(__dirname, "..", "..", "..", "www"),
  resolve(__dirname, "..", "..", "www"),
  resolve(process.cwd(), "www"),
].find((candidate) => existsSync(join(candidate, "index.html"))) ??
  resolve(process.cwd(), "www");

const CONTENT_TYPES: Readonly<Record<string, string>> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".ico": "image/x-icon",
  ".map": "application/json; charset=utf-8",
  ".woff2": "font/woff2",
};

/**
 * Cabeceras de seguridad, iguales para todo lo que se sirve.
 *
 * `img-src` admite `blob:` porque la vista previa de la foto recién tomada es una
 * URL de objeto, y el dominio de Blob Storage porque la pantalla de moderación
 * carga las imágenes con una firma temporal. `geolocation=()` deja por escrito
 * lo que el código ya cumple: esta aplicación no pide ubicación.
 */
const SECURITY_HEADERS: Readonly<Record<string, string>> = {
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Permissions-Policy": "geolocation=(), microphone=(), interest-cohort=()",
  "Content-Security-Policy":
    "default-src 'self'; img-src 'self' blob: data: https://*.blob.core.windows.net; " +
    "media-src 'self' blob:; connect-src 'self' https://*.blob.core.windows.net; " +
    "style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'none'; " +
    "base-uri 'self'; form-action 'self'",
};

/** Los recursos con huella en el nombre se cachean para siempre; el resto, nunca. */
function cacheControlFor(path: string): string {
  return path.startsWith("assets/") ? "public, max-age=31536000, immutable" : "no-cache";
}

export async function serveSite(request: HttpRequest): Promise<HttpResponseInit> {
  const requested = decodeURIComponent(new URL(request.url).pathname).replace(/^\/+/, "");

  // Rutas de la plataforma que este atrapatodo no debe contestar jamás.
  //
  // No es hipotético: el middleware de autenticación atiende `/.auth/login/*`
  // pero deja pasar `/.auth/me`, que llegó hasta aquí y se sirvió como la página
  // de la aplicación. El cliente esperaba JSON, recibió HTML, y toda sesión
  // parecía anónima sin un solo error en ningún log. Devolver 404 hace que un
  // fallo así se vea en vez de disfrazarse de «nadie ha iniciado sesión».
  if (requested === ".auth" || requested.startsWith(".auth/")) {
    return { status: 404, body: "Ruta de plataforma no disponible." };
  }

  // Normalizar y comprobar que sigue dentro de la raíz: sin esto, una ruta con
  // `..` serviría cualquier archivo del contenedor, incluida la configuración
  // con las cadenas de conexión.
  const candidate = resolve(join(WEB_ROOT, normalize(requested)));
  const insideRoot = candidate === WEB_ROOT || candidate.startsWith(WEB_ROOT + sep);

  const file =
    insideRoot && requested.length > 0 && existsSync(candidate) && statSync(candidate).isFile()
      ? candidate
      : join(WEB_ROOT, "index.html");

  if (!existsSync(file)) {
    return { status: 404, body: "La aplicación web no está publicada." };
  }

  const isFallback = file.endsWith("index.html");
  return {
    status: 200,
    headers: {
      ...SECURITY_HEADERS,
      "content-type": CONTENT_TYPES[extname(file)] ?? "application/octet-stream",
      "cache-control": isFallback ? "no-cache" : cacheControlFor(requested),
    },
    body: createReadStream(file),
  };
}

app.http("serveSite", {
  methods: ["GET"],
  authLevel: "anonymous",
  // Atrapatodo. Las rutas de `/api/*` y `/.auth/*` se resuelven antes: las
  // primeras porque tienen su propia ruta registrada, las segundas porque las
  // atiende el front-end de autenticación antes de llegar al código.
  route: "{*path}",
  handler: serveSite,
});
