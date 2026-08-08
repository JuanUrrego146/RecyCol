/**
 * La recompensa lee el perfil normativo real. Estas pruebas fijan que las
 * decisiones de producto de Juan (§6) llegan intactas a la pantalla, y de paso
 * detectan si alguien cambia `co.json` de una forma que rompa este consumidor.
 */

import { describe, expect, it } from "vitest";
import { MATERIALS } from "./materials";
import { REGULATION_NAME, resolveBin } from "./profile";

describe("resolución de caneca", () => {
  it("cubre las once clases de la taxonomía", () => {
    for (const material of MATERIALS) {
      expect(() => resolveBin(material, null)).not.toThrow();
    }
  });

  it("manda el vaso de café limpio a la blanca y el sucio a la negra", () => {
    // Decisión 3 de §6: el cartón para bebidas exige vista interior.
    expect(resolveBin("BEVERAGE_CARTON", "CLEAN").binId).toBe("white");
    const dirty = resolveBin("BEVERAGE_CARTON", "LIQUID");
    expect(dirty.binId).toBe("black");
    expect(dirty.degraded).toBe(true);
  });

  it("degrada el cartón con grasa, como la caja de pizza", () => {
    // Decisión 4 de §6, aprobada el 07/08.
    expect(resolveBin("CARDBOARD", "CLEAN").binId).toBe("white");
    expect(resolveBin("CARDBOARD", "GREASE").binId).toBe("black");
  });

  it("no degrada donde el perfil no define alternativa", () => {
    const organic = resolveBin("ORGANIC", "RESIDUE");
    expect(organic.route).toBe("ORGANIC");
    expect(organic.degraded).toBe(false);
  });

  it("manda pilas y electrónicos a punto de recolección especial", () => {
    // Decisión 1 de §6: ELECTRONIC entra con ruta a posconsumo, igual que BATTERY.
    expect(resolveBin("BATTERY", null).route).toBe("SPECIAL_COLLECTION");
    expect(resolveBin("ELECTRONIC", null).route).toBe("SPECIAL_COLLECTION");
  });

  it("no degrada cuando no se declaró el estado", () => {
    expect(resolveBin("PLASTIC", null).binId).toBe("white");
    expect(resolveBin("PLASTIC", null).degraded).toBe(false);
  });

  it("siempre acompaña el color con nombre y justificación", () => {
    // RNF-010: nunca se identifica una caneca solo por color.
    for (const material of MATERIALS) {
      const decision = resolveBin(material, null);
      expect(decision.displayName.length).toBeGreaterThan(0);
      expect(decision.justification.length).toBeGreaterThan(0);
      expect(decision.colorHex).toMatch(/^#[0-9A-Fa-f]{6}$/);
    }
  });

  it("cita la norma vigente", () => {
    expect(REGULATION_NAME).toContain("2184");
  });
});
