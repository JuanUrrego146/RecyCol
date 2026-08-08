/**
 * Autorización de las rutas de administración.
 *
 * Static Web Apps resuelve el inicio de sesión y reenvía la identidad en la
 * cabecera `x-ms-client-principal`, en base64. La ruta `/revisar` ya está
 * protegida en `staticwebapp.config.json`, **pero eso solo protege la página**:
 * `/api/review/*` y `/api/export/*` son extremos HTTP que cualquiera puede
 * llamar a mano. Por eso se vuelve a comprobar el rol aquí.
 *
 * La cabecera la inyecta la plataforma y **sobrescribe** cualquier valor que
 * mande el cliente, así que confiar en ella es correcto — dentro de Static Web
 * Apps. Ejecutando las funciones en local no hay plataforma que la inyecte, y
 * por eso el desarrollo local exige activar explícitamente `ALLOW_LOCAL_ADMIN`.
 */

import type { HttpRequest } from "@azure/functions";
import { config } from "./config";

export interface ClientPrincipal {
  userId: string;
  userDetails: string;
  identityProvider: string;
  userRoles: string[];
}

export function readPrincipal(request: HttpRequest): ClientPrincipal | null {
  const header = request.headers.get("x-ms-client-principal");
  if (!header) return null;
  try {
    const decoded = Buffer.from(header, "base64").toString("utf8");
    const parsed = JSON.parse(decoded) as Partial<ClientPrincipal>;
    return {
      userId: parsed.userId ?? "",
      userDetails: parsed.userDetails ?? "",
      identityProvider: parsed.identityProvider ?? "",
      userRoles: Array.isArray(parsed.userRoles) ? parsed.userRoles : [],
    };
  } catch {
    return null;
  }
}

export function isAdministrator(request: HttpRequest): boolean {
  // Puerta explícita para `swa start` / `func start` en la máquina de desarrollo.
  // Nunca se define en Azure; si alguien la definiera, la moderación quedaría
  // abierta, así que el despliegue documentado no la incluye.
  if (process.env.ALLOW_LOCAL_ADMIN === "true") return true;
  const principal = readPrincipal(request);
  return principal?.userRoles.includes(config.adminRole) ?? false;
}
