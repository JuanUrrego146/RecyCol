/**
 * Freno diario por dirección de red.
 *
 * ## Por qué no basta con el tope por aportante
 *
 * `DAILY_CAPTURE_LIMIT` cuenta contra `contributorId`, y `contributorId` **llega
 * en el cuerpo de la petición**: es un UUID que genera el navegador. Un guion
 * que ponga uno nuevo en cada llamada no roza el tope — se lo salta entero, y con
 * él la única barrera que había entre un enlace público y una cuenta de
 * almacenamiento. Es un tope que solo frena a quien no intenta saltárselo.
 *
 * Contar además por dirección de origen cierra eso, porque la dirección no la
 * elige el cliente: la pone la plataforma. Los dos topes se suman, no se
 * sustituyen — el de aportante sigue siendo el que protege el equilibrio del
 * dataset frente a una sola persona muy entusiasta.
 *
 * ## Por qué la última entrada de `x-forwarded-for` y no la primera
 *
 * La convención de la cabecera es «cliente, proxy1, proxy2…», así que lo natural
 * sería leer la primera. **Aquí sería justo lo contrario de lo que hay que
 * hacer**: App Service *añade* la dirección real al final de lo que traiga la
 * petición, así que un cliente que mande `x-forwarded-for: 1.2.3.4` produce
 * `1.2.3.4, <dirección real>`. Quedarse con la primera es dejar que el atacante
 * escriba su propia clave de cuota, que es exactamente el fallo que esto viene a
 * arreglar. La última es la que pone la plataforma y la única que no se falsea.
 *
 * ## Por qué la dirección no se guarda en claro
 *
 * Una dirección de red es dato personal y esta plataforma promete no recoger
 * ninguno de quien aporta sin cuenta. Lo que se guarda es un resumen SHA-256 de
 * «día + dirección», y solo un contador junto a él: ni identificador de captura,
 * ni de aportante, ni nada que permita reconstruir quién subió qué. Al cambiar el
 * día cambia la clave, así que la tabla tampoco sirve como historial de actividad
 * de una dirección.
 *
 * Sin engañarse: un resumen de una dirección IPv4 se puede recorrer entero por
 * fuerza bruta si alguien se hace con la tabla. Esto no es anonimización, es no
 * dejar direcciones escritas en claro en un almacén que se exporta a mano.
 */

import { createHash } from "node:crypto";
import type { HttpRequest } from "@azure/functions";

/**
 * Tope diario por dirección de red.
 *
 * Deliberadamente alto: en una universidad muchísima gente sale por la misma
 * dirección, así que este número no mide personas, mide tráfico desde un punto de
 * salida. **Si alguna vez frena una jornada de captura legítima, se sube aquí y
 * se vuelve a desplegar**: es un freno antiabuso, no una cuota de producto.
 *
 * Tiene que quedar **por encima de `DAILY_CAPTURE_LIMIT`**, que son 400 por
 * aportante. Si quedara por debajo, el tope compartido frenaría antes que el
 * individual y una sola persona muy entusiasta podría dejar sin aportar a todo el
 * campus. Con 800 caben dos jornadas completas de las de tope, o cuarenta
 * personas a veinte fotos, que es el uso realista. `ratelimit.test.ts` fija esa
 * relación para que no se rompa al tocar cualquiera de los dos números.
 */
export const DAILY_IP_CAPTURE_LIMIT = 800;

/**
 * Dirección de origen de la petición, o `null` si no hay forma de saberla.
 *
 * `null` en desarrollo local y en cualquier despliegue sin proxy delante. Se
 * traduce en «sin tope por dirección»: es preferible a inventarse una clave
 * compartida, que metería a todo el mundo en el mismo cubo y bloquearía a la
 * segunda persona que aportara.
 */
export function clientIp(request: HttpRequest): string | null {
  const forwarded = request.headers.get("x-forwarded-for");
  if (!forwarded) return null;
  const hops = forwarded
    .split(",")
    .map((hop) => hop.trim())
    .filter((hop) => hop.length > 0);
  const last = hops[hops.length - 1];
  if (last === undefined) return null;
  const address = stripPort(last).toLowerCase();
  return address.length > 0 ? address : null;
}

/** App Service escribe `1.2.3.4:56789`; IPv6 llega entre corchetes cuando lleva puerto. */
function stripPort(value: string): string {
  if (value.startsWith("[")) {
    const close = value.indexOf("]");
    return close > 1 ? value.slice(1, close) : value;
  }
  const colons = value.split(":").length - 1;
  // Un solo `:` es IPv4 con puerto. Varios son IPv6 sin corchetes, que no lo lleva.
  return colons === 1 ? value.slice(0, value.lastIndexOf(":")) : value;
}

/** Clave de fila del contador: resumen de «día + dirección». Ver la cabecera. */
export function ipQuotaKey(day: string, ip: string): string {
  return createHash("sha256").update(`${day}|${ip}`).digest("hex").slice(0, 32);
}
