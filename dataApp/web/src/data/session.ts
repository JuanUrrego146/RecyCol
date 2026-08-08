/**
 * Sesión: quién ha entrado, si es que ha entrado alguien.
 *
 * **No manejamos contraseñas.** La autenticación la resuelve entera App Service
 * Authentication: la aplicación solo consulta `/.auth/me` para saber quién es y
 * redirige a `/.auth/login/aad` para entrar. No hay registro que construir, ni
 * cifrado de contraseñas que mantener, ni recuperación de cuenta, ni una
 * filtración de credenciales que temer, porque aquí no hay ninguna credencial.
 *
 * Y trae una propiedad que importa más de lo que parece: entrar con el correo de
 * la universidad **demuestra** la pertenencia a la UMNG. Si un profesor va a dar
 * puntos con esta lista, no puede valer lo mismo eso que escribir a mano «yo
 * estudio allí».
 *
 * > Esto hablaba antes con Static Web Apps, que devuelve
 * > `{clientPrincipal: {identityProvider, userId, userDetails}}`. App Service
 * > devuelve un array de identidades con sus claims en crudo. Se migró porque
 * > Static Web Apps no existe en ninguna región que la suscripción del proyecto
 * > permita.
 */

import { isUmngEmail } from "../domain/account";

/** Identidad ya normalizada, con la forma que el resto de la aplicación espera. */
export interface ClientPrincipal {
  readonly identityProvider: string;
  /** Identificador estable del directorio (`oid`), no el correo. */
  readonly userId: string;
  /** Correo o nombre principal. Con él se comprueba el dominio institucional. */
  readonly userDetails: string;
  readonly displayName: string | null;
}

interface AuthMeEntry {
  provider_name?: string;
  user_id?: string;
  user_claims?: { typ: string; val: string }[];
}

/**
 * Proveedores de acceso.
 *
 * Solo Microsoft. Cubre las cuentas institucionales `@unimilitar.edu.co` —que
 * además acreditan la pertenencia— y cualquier cuenta personal de Microsoft.
 * Google se descartó el 07/08 por innecesario. GitHub exigiría registrar una
 * aplicación OAuth aparte y aporta poco a este público.
 *
 * Quien no tenga ninguna aporta de forma anónima, que sigue siendo el camino por
 * defecto.
 */
export interface LoginProvider {
  readonly id: string;
  readonly name: string;
  readonly glyph: string;
  readonly description: string;
}

export const LOGIN_PROVIDERS: readonly LoginProvider[] = [
  {
    id: "aad",
    name: "Cuenta Microsoft",
    glyph: "🎓",
    description: "Tu correo @unimilitar.edu.co, o cualquier cuenta Microsoft",
  },
];

export function loginUrl(provider: string, returnTo = "/"): string {
  // App Service usa `post_login_redirect_url`; Static Web Apps usaba
  // `post_login_redirect_uri`. Un carácter de diferencia y el retorno se pierde.
  return `/.auth/login/${provider}?post_login_redirect_url=${encodeURIComponent(returnTo)}`;
}

export function logoutUrl(returnTo = "/"): string {
  return `/.auth/logout?post_logout_redirect_uri=${encodeURIComponent(returnTo)}`;
}

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

/**
 * Lee la identidad activa. Devuelve `null` si nadie ha entrado — que es un
 * estado perfectamente válido: la cuenta es opcional.
 */
export async function readPrincipal(): Promise<ClientPrincipal | null> {
  try {
    const response = await fetch("/.auth/me", { headers: { accept: "application/json" } });
    if (!response.ok) return null;

    const body = (await response.json()) as AuthMeEntry[] | { clientPrincipal?: unknown };
    const entry = Array.isArray(body) ? body[0] : null;
    if (!entry) return null;

    const claims = new Map((entry.user_claims ?? []).map((claim) => [claim.typ, claim.val]));
    const userDetails = pick(claims, EMAIL_CLAIMS) ?? entry.user_id ?? "";
    const userId = pick(claims, OBJECT_ID_CLAIMS) ?? entry.user_id ?? "";
    if (!userId) return null;

    return {
      identityProvider: entry.provider_name ?? "aad",
      userId,
      userDetails,
      displayName: pick(claims, NAME_CLAIMS),
    };
  } catch {
    // En desarrollo local la ruta no existe y devuelve el HTML de la aplicación.
    // No es un error: simplemente no hay sesión y se aporta de forma anónima.
    return null;
  }
}

/**
 * ¿La identidad con la que entró acredita pertenencia a la UMNG?
 *
 * Solo cuenta el correo institucional. Alguien de la universidad que entre con
 * su cuenta personal puede declararse de la UMNG, pero queda como **declarado**,
 * no comprobado, y el informe lo distingue.
 */
export function verifiesUmng(principal: ClientPrincipal): boolean {
  return isUmngEmail(principal.userDetails);
}
