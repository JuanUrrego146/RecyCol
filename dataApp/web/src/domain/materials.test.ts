import { describe, expect, it } from "vitest";
import { MATERIALS, MATERIAL_INFO, isMaterial, materialName } from "./materials";

/**
 * La lista literal de esta prueba es la del enum `WasteMaterial` del dominio
 * Kotlin y la de `target_taxonomy.materials` en `ml/taxonomy/label_mapping.yaml`.
 * Si alguien añade una clase allí y no aquí, esta prueba lo dice antes de que
 * lleguen etiquetas que la ingesta no sabe mapear.
 */
const TAXONOMY_OF_RECORD = [
  "PLASTIC",
  "PAPER",
  "CARDBOARD",
  "BEVERAGE_CARTON",
  "GLASS",
  "METAL",
  "ORGANIC",
  "TEXTILE",
  "BATTERY",
  "ELECTRONIC",
  "RESIDUAL",
];

describe("taxonomía", () => {
  it("coincide con la del dominio, en el mismo orden", () => {
    expect([...MATERIALS]).toEqual(TAXONOMY_OF_RECORD);
  });

  it("describe las once clases con nombre, glifo y ejemplos", () => {
    for (const material of MATERIALS) {
      const info = MATERIAL_INFO[material];
      expect(info.name.length).toBeGreaterThan(0);
      expect(info.glyph.length).toBeGreaterThan(0);
      expect(info.examples.length).toBeGreaterThan(0);
    }
  });

  it("desambigua los pares que de verdad se confunden", () => {
    // La confusión BEVERAGE_CARTON ↔ CARDBOARD es de las caras: se salta la
    // inspección del vaso y la contaminación no se detecta (§7).
    expect(MATERIAL_INFO.BEVERAGE_CARTON.hint).toBeDefined();
    expect(MATERIAL_INFO.CARDBOARD.hint).toBeDefined();
    expect(MATERIAL_INFO.PAPER.hint).toBeDefined();
  });

  it("reconoce y rechaza cadenas sueltas", () => {
    expect(isMaterial("PLASTIC")).toBe(true);
    expect(isMaterial("plastico")).toBe(false);
    expect(isMaterial("")).toBe(false);
  });

  it("da nombre visible en español", () => {
    expect(materialName("BEVERAGE_CARTON")).toBe("Cartón de bebidas");
  });
});
