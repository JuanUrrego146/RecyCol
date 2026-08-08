/**
 * El tope por dirección es la única barrera que no depende de un dato que manda
 * el cliente. Si se lee mal la cabecera deja de serlo, así que lo que se prueba
 * aquí es sobre todo **de dónde** se saca la dirección: quedarse con la primera
 * entrada de `x-forwarded-for` —que es lo que dice la convención y lo que haría
 * cualquiera— devolvería el control al atacante.
 */

import { describe, expect, it } from "vitest";
import type { HttpRequest } from "@azure/functions";
import { DAILY_CAPTURE_LIMIT } from "./model";
import { DAILY_IP_CAPTURE_LIMIT, clientIp, ipQuotaKey } from "./ratelimit";

function requestFrom(forwarded: string | null): HttpRequest {
  return {
    headers: { get: (name: string) => (name === "x-forwarded-for" ? forwarded : null) },
  } as unknown as HttpRequest;
}

describe("dirección de origen", () => {
  it("devuelve null sin cabecera: en local no hay tope", () => {
    expect(clientIp(requestFrom(null))).toBeNull();
    expect(clientIp(requestFrom("   "))).toBeNull();
  });

  it("quita el puerto que añade App Service", () => {
    expect(clientIp(requestFrom("190.24.1.7:56789"))).toBe("190.24.1.7");
    expect(clientIp(requestFrom("190.24.1.7"))).toBe("190.24.1.7");
  });

  it("se queda con la ÚLTIMA entrada, que es la que pone la plataforma", () => {
    // Un cliente que manda su propia cabecera consigue esto. Leer la primera
    // dejaría que se inventara una clave de cuota distinta en cada petición.
    expect(clientIp(requestFrom("1.2.3.4, 190.24.1.7:443"))).toBe("190.24.1.7");
    expect(clientIp(requestFrom("no-es-una-ip, 190.24.1.7:443"))).toBe("190.24.1.7");
  });

  it("entiende IPv6 con y sin corchetes", () => {
    expect(clientIp(requestFrom("[2001:db8::1]:443"))).toBe("2001:db8::1");
    expect(clientIp(requestFrom("2001:DB8::1"))).toBe("2001:db8::1");
  });
});

describe("clave del contador", () => {
  it("no contiene la dirección", () => {
    const key = ipQuotaKey("2026-08-08", "190.24.1.7");
    expect(key).not.toContain("190.24.1.7");
    expect(key).toMatch(/^[0-9a-f]{32}$/);
  });

  it("es estable dentro del día y distinta al cambiar de día", () => {
    // Estable, o el tope no contaría; distinta entre días, o la tabla sería un
    // historial de actividad por dirección.
    expect(ipQuotaKey("2026-08-08", "190.24.1.7")).toBe(ipQuotaKey("2026-08-08", "190.24.1.7"));
    expect(ipQuotaKey("2026-08-09", "190.24.1.7")).not.toBe(ipQuotaKey("2026-08-08", "190.24.1.7"));
  });

  it("distingue direcciones distintas", () => {
    expect(ipQuotaKey("2026-08-08", "190.24.1.7")).not.toBe(ipQuotaKey("2026-08-08", "190.24.1.8"));
  });
});

describe("tope", () => {
  it("queda por encima del tope por aportante", () => {
    // Es la relación que importa, no el número. Por debajo, el tope compartido
    // frenaría antes que el individual: una sola persona en el tope de sus 400
    // dejaría sin aportar a todo el que salga por esa misma dirección.
    expect(DAILY_IP_CAPTURE_LIMIT).toBeGreaterThan(DAILY_CAPTURE_LIMIT);
  });
});
