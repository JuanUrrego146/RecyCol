/*
 * Service worker mínimo: solo el armazón de la aplicación.
 *
 * Deliberadamente no toca `/api` ni el almacenamiento de imágenes. Cachear
 * peticiones de escritura sería una forma elegante de perder aportes; la
 * durabilidad de lo pendiente vive en IndexedDB (`src/data/uploadQueue.ts`), que
 * sobrevive a cerrar el navegador y no depende de este archivo.
 *
 * Su único trabajo real: que abrir la aplicación sin cobertura muestre la
 * interfaz —con sus fotos pendientes— en vez del dinosaurio del navegador.
 */

const CACHE = "recycol-aporta-v1";
const SHELL = ["/", "/index.html", "/manifest.webmanifest", "/icon.svg"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)))),
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api")) return;

  // Nada de `/.auth/*` toca la caché, jamás.
  //
  // Son las rutas de identidad de la plataforma. Guardar una respuesta suya en
  // un almacén compartido por todo el origen significa que, en un equipo
  // compartido —un portátil de campus, el móvil que se pasa de mano en mano—,
  // la siguiente persona podría recibir la sesión de la anterior. El coste de
  // excluirlas es cero: nunca hacen falta sin conexión.
  if (url.pathname.startsWith("/.auth")) return;

  // Red primero y caché como red de seguridad: así una versión nueva de la
  // aplicación llega sin tener que borrar datos del sitio.
  event.respondWith(
    fetch(request)
      .then((response) => {
        const copy = response.clone();
        void caches.open(CACHE).then((cache) => cache.put(request, copy));
        return response;
      })
      .catch(() =>
        caches
          .match(request)
          .then((cached) => cached ?? caches.match("/index.html").then((shell) => shell ?? Response.error())),
      ),
  );
});
