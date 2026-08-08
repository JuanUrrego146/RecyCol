/**
 * Identidad de quien llama, sobre App Service Authentication («Easy Auth»).
 *
 * La plataforma resuelve el inicio de sesión —`/.auth/login/aad`, `/.auth/me`,
 * `/.auth/logout`— y reenvía la identidad ya validada en la cabecera
 * `X-MS-CLIENT-PRINCIPAL`, en base64. **Nosotros no vemos ni guardamos ninguna
 * contraseña**, y no hay token que validar a mano: la cabecera la inyecta el
 * front-end de autenticación y sobrescribe cualquier valor que mande el cliente.
 *
 * > Esto vivía sobre Static Web Apps, que emite la misma cabecera con **otro
 * > formato**: `{identityProvider, userId, userDetails, userRoles}`. App Service
 * > usa el formato de claims de .NET: `{auth_typ, claims:[{typ,val}]}`. Se migró
 * > porque Static Web Apps no existe en ninguna región que la suscripción del
 * > proyecto permita (ver la cabecera de `infra/provision.sh`).
 *
 * ## Administración por lista de correos
 *
 * Static Web Apps traía invitaciones con roles; App Service no. En su lugar,
 * `ADMIN_EMAILS` lleva los correos autorizados. Para dos o tres personas es más
 * simple, más visible y más fácil de revocar que un sistema de roles, y el
 * cambio se aplica sin volver a desplegar.
 */

import type { HttpRequest } from "@azure/functions";

export interface ClientPrincipal {
  /** Identificador estable de la persona. Es el `oid` del directorio, no el correo. */
  userId: string;
  /** Correo o nombre principal. Con él se comprueba el dominio institucional. */
  userDetails: string;
  identityProvider: string;
  displayName: string | null;
}

interface EasyAuthPrincipal {
  auth_typ?: string;
  name_typ?: string;
  role_typ?: string;
  claims?: { typ: string; val: string }[];
}

/**
 * Nombres de claim que puede traer el mismo dato.
 *
 * Una cuenta de organización manda `preferred_username`; una personal, a veces
 * solo el claim largo de esquema XML. Mirar uno solo deja fuera a la mitad de la
 * gente.
 */
const OBJECT_ID_CLAIMS = [
  "http://schemas.microsoft.com/identity/claims/objectidentifier",
  "oid",
  "sub",
  "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier",
];

const EMAIL_CLAIMS = [
  "preferred_username",
  "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
  "email",
  "upn",
  "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/upn",
];

const NAME_CLAIMS = ["name", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name"];

function pick(claims: Map<string, string>, candidates: readonly string[]): string | null {
  for (const candidate of candidates) {
    const value = claims.get(candidate);
    if (value) return value;
  }
  return null;
}

export function readPrincipal(request: HttpRequest): ClientPrincipal | null {
  const header = request.headers.get("x-ms-client-principal");
  if (!header) return null;

  try {
    const parsed = JSON.parse(Buffer.from(header, "base64").toString("utf8")) as EasyAuthPrincipal;
    const claims = new Map((parsed.claims ?? []).map((claim) => [claim.typ, claim.val]));

    const userDetails = pick(claims, EMAIL_CLAIMS);
    const userId = pick(claims, OBJECT_ID_CLAIMS) ?? userDetails;
    if (!userId) return null;

    return {
      userId,
      userDetails: userDetails ?? "",
      identityProvider: parsed.auth_typ ?? "aad",
      displayName: pick(claims, NAME_CLAIMS),
    };
  } catch {
    return null;
  }
}

/**
 * Correos con permiso de moderación y exportación.
 *
 * Se comparan en minúsculas y sin espacios: una coma de más en la variable de
 * entorno no debe dejar a nadie fuera de su propia herramienta de moderación.
 */
function adminEmails(): Set<string> {
  return new Set(
    (process.env.ADMIN_EMAILS ?? "")
      .split(",")
      .map((email) => email.trim().toLowerCase())
      .filter((email) => email.length > 0),
  );
}

export function isAdministrator(request: HttpRequest): boolean {
  // Puerta explícita para el desarrollo local, donde no hay plataforma que
  // inyecte la cabecera. Nunca se define en Azure; si estuviera puesta, la
  // moderación y la exportación quedarían abiertas a cualquiera.
  if (process.env.ALLOW_LOCAL_ADMIN === "true") return true;

  const principal = readPrincipal(request);
  if (!principal?.userDetails) return false;
  return adminEmails().has(principal.userDetails.trim().toLowerCase());
}
