/**
 * La identidad decide dos cosas caras de equivocar: quién puede moderar y
 * exportar el dataset, y a quién se le acredita pertenecer a la UMNG cuando de
 * ello puede depender una nota. Por eso se prueba el análisis de la cabecera
 * campo a campo, incluidos los formatos raros que manda cada proveedor.
 */

import { afterEach, describe, expect, it } from "vitest";
import type { HttpRequest } from "@azure/functions";
import { isAdministrator, readPrincipal } from "./auth";

/** Petición mínima con solo lo que `readPrincipal` mira. */
function requestWith(principal: unknown | null): HttpRequest {
  const header = principal === null ? null : Buffer.from(JSON.stringify(principal)).toString("base64");
  return {
    headers: { get: (name: string) => (name === "x-ms-client-principal" ? header : null) },
  } as unknown as HttpRequest;
}

const CLAIM_OID = "http://schemas.microsoft.com/identity/claims/objectidentifier";
const CLAIM_EMAIL_XML = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

function easyAuth(claims: { typ: string; val: string }[], authType = "aad") {
  return { auth_typ: authType, claims };
}

const entorno = { ...process.env };
afterEach(() => {
  process.env = { ...entorno };
});

describe("lectura de identidad", () => {
  it("devuelve null sin cabecera", () => {
    expect(readPrincipal(requestWith(null))).toBeNull();
  });

  it("devuelve null si la cabecera no es descifrable", () => {
    const roto = { headers: { get: () => "no-es-base64-valido!!" } } as unknown as HttpRequest;
    expect(readPrincipal(roto)).toBeNull();
  });

  it("lee una cuenta de organización", () => {
    const principal = readPrincipal(
      requestWith(
        easyAuth([
          { typ: CLAIM_OID, val: "oid-123" },
          { typ: "preferred_username", val: "est.juan.perez@unimilitar.edu.co" },
          { typ: "name", val: "Juan Pérez" },
        ]),
      ),
    );
    expect(principal?.userId).toBe("oid-123");
    expect(principal?.userDetails).toBe("est.juan.perez@unimilitar.edu.co");
    expect(principal?.displayName).toBe("Juan Pérez");
  });

  it("acepta los nombres largos de claim que manda una cuenta personal", () => {
    // Mirar solo `preferred_username` dejaría fuera a media plataforma.
    const principal = readPrincipal(
      requestWith(easyAuth([{ typ: CLAIM_EMAIL_XML, val: "alguien@outlook.com" }])),
    );
    expect(principal?.userDetails).toBe("alguien@outlook.com");
    // Sin claim de identificador, el correo sirve de identidad estable.
    expect(principal?.userId).toBe("alguien@outlook.com");
  });

  it("prefiere el identificador del directorio al correo", () => {
    // El correo de alguien puede cambiar; el `oid` no. Si la identidad colgara
    // del correo, un cambio de nombre partiría sus aportes en dos personas.
    const principal = readPrincipal(
      requestWith(
        easyAuth([
          { typ: CLAIM_OID, val: "oid-estable" },
          { typ: "preferred_username", val: "cambia@unimilitar.edu.co" },
        ]),
      ),
    );
    expect(principal?.userId).toBe("oid-estable");
  });

  it("devuelve null si no hay ni identificador ni correo", () => {
    expect(readPrincipal(requestWith(easyAuth([{ typ: "algo", val: "otra cosa" }])))).toBeNull();
  });
});

describe("permiso de administración", () => {
  it("lo niega sin sesión", () => {
    process.env.ADMIN_EMAILS = "jefe@ejemplo.com";
    expect(isAdministrator(requestWith(null))).toBe(false);
  });

  it("lo niega a quien no está en la lista", () => {
    process.env.ADMIN_EMAILS = "jefe@ejemplo.com";
    const request = requestWith(easyAuth([{ typ: "preferred_username", val: "otro@ejemplo.com" }]));
    expect(isAdministrator(request)).toBe(false);
  });

  it("lo concede a quien sí está", () => {
    process.env.ADMIN_EMAILS = "jefe@ejemplo.com";
    const request = requestWith(easyAuth([{ typ: "preferred_username", val: "jefe@ejemplo.com" }]));
    expect(isAdministrator(request)).toBe(true);
  });

  it("no se deja engañar por mayúsculas ni espacios", () => {
    process.env.ADMIN_EMAILS = "  Jefe@Ejemplo.com , otra@ejemplo.com ";
    const request = requestWith(easyAuth([{ typ: "preferred_username", val: "JEFE@ejemplo.COM" }]));
    expect(isAdministrator(request)).toBe(true);
  });

  it("lo niega si la lista está vacía", () => {
    // Sin lista no hay administradores: es preferible quedarse fuera de la propia
    // herramienta de moderación a dejarla abierta.
    process.env.ADMIN_EMAILS = "";
    const request = requestWith(easyAuth([{ typ: "preferred_username", val: "jefe@ejemplo.com" }]));
    expect(isAdministrator(request)).toBe(false);
  });

  it("la puerta local solo se abre con la variable explícita", () => {
    process.env.ADMIN_EMAILS = "";
    process.env.ALLOW_LOCAL_ADMIN = "true";
    expect(isAdministrator(requestWith(null))).toBe(true);
    process.env.ALLOW_LOCAL_ADMIN = "false";
    expect(isAdministrator(requestWith(null))).toBe(false);
  });
});
