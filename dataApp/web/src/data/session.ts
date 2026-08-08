/**
 * Sesión: quién ha entrado, si es que ha entrado alguien.
 *
 * **No manejamos contraseñas.** La autenticación la resuelve entera Static Web
 * Apps con proveedores ya integrados: la aplicación solo consulta `/.auth/me`
 * para saber quién es y redirige a `/.auth/login/<proveedor>` para entrar. No
 * hay registro que construir, ni cifrado de contraseñas que mantener, ni
 * recuperación de cuenta, ni una filtración de credenciales que temer, porque
 * aquí no hay ninguna credencial.
 *
 * Y trae una propiedad que importa más de lo que parece: entrar con el correo de
 * la universidad **demuestra** la pertenencia a la UMNG. Si un profesor va a dar
 * puntos con esta lista, no puede valer lo mismo eso que escribir a mano «yo
 * estudio allí».
 */

import { isUmngEmail } from "../domain/account";

/** Identidad tal y como la devuelve Static Web Apps. */
export interface ClientPrincipal {
  readonly identityProvider: string;
  readonly userId: string;
  readonly userDetails: string;
  readonly userRoles: readonly string[];
}

/**
 * Proveedores de acceso.
 *
 * Microsoft y GitHub vienen incluidos en el plan gratuito. **Google exige el
 * plan Standard** (9 USD/mes) porque entra como proveedor OpenID personalizado:
 * está aquí listo para activarse cambiando `enabled`, junto con el paso de
 * configuración correspondiente en `docs/DESPLIEGUE.md`.
 */
export interface LoginProvider {
  readonly id: string;
  readonly name: string;
  readonly glyph: string;
  readonly description: string;
  readonly enabled: boolean;
}

export const LOGIN_PROVIDERS: readonly LoginProvider[] = [
  {
    id: "aad",
    name: "Cuenta Microsoft",
    glyph: "🎓",
    description: "Tu correo @unimilitar.edu.co, o cualquier cuenta Microsoft",
    enabled: true,
  },
  {
    id: "github",
    name: "GitHub",
    glyph: "💻",
    description: "Si ya tienes cuenta",
    enabled: true,
  },
  {
    id: "google",
    name: "Google",
    glyph: "🔵",
    description: "Requiere el plan Standard de Azure",
    enabled: false,
  },
];

export function loginUrl(provider: string, returnTo = "/"): string {
  return `/.auth/login/${provider}?post_login_redirect_uri=${encodeURIComponent(returnTo)}`;
}

export function logoutUrl(returnTo = "/"): string {
  return `/.auth/logout?post_logout_redirect_uri=${encodeURIComponent(returnTo)}`;
}

/**
 * Lee la identidad activa. Devuelve `null` si nadie ha entrado — que es un
 * estado perfectamente válido: la cuenta es opcional.
 */
export async function readPrincipal(): Promise<ClientPrincipal | null> {
  try {
    const response = await fetch("/.auth/me");
    if (!response.ok) return null;
    const body = (await response.json()) as { clientPrincipal?: ClientPrincipal | null };
    const principal = body.clientPrincipal;
    if (!principal?.userId) return null;
    return principal;
  } catch {
    // En desarrollo local sin Static Web Apps la ruta no existe. No es un error:
    // simplemente no hay sesión y se aporta de forma anónima.
    return null;
  }
}

/**
 * ¿La identidad con la que entró acredita pertenencia a la UMNG?
 *
 * Solo cuenta el correo institucional a través de Microsoft. Alguien de la
 * universidad que entre con su cuenta personal puede declararse de la UMNG, pero
 * queda como **declarado**, no comprobado, y el informe lo distingue.
 */
export function verifiesUmng(principal: ClientPrincipal): boolean {
  return principal.identityProvider === "aad" && isUmngEmail(principal.userDetails);
}
