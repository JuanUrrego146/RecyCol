import { describe, expect, it } from "vitest";
import { MATERIALS } from "./materials";
import {
  CONTAMINATION_OPTIONS,
  contaminationPolicy,
  contaminationQuestion,
  isContaminated,
} from "./contamination";

describe("política de contaminación", () => {
  it("la exige exactamente en las tres clases de fibra", () => {
    // §10: la captura prioritaria es el estado de contaminación en cartón y
    // papel; el cartón de bebidas entra porque su regla de negocio depende de si
    // queda líquido dentro (decisión 3 de §6).
    const required = MATERIALS.filter((material) => contaminationPolicy(material) === "REQUIRED");
    expect(new Set(required)).toEqual(new Set(["PAPER", "CARDBOARD", "BEVERAGE_CARTON"]));
  });

  it("la ofrece sin exigirla en envases lavables", () => {
    // Plástico, vidrio y metal se enjuagan y se reciclan igual: la respuesta no
    // cambia la caneca (decisión 9 de §6).
    for (const material of ["PLASTIC", "GLASS", "METAL"] as const) {
      expect(contaminationPolicy(material)).toBe("OPTIONAL");
    }
  });

  it("no la pregunta donde no aporta nada", () => {
    for (const material of ["ORGANIC", "TEXTILE", "BATTERY", "ELECTRONIC", "RESIDUAL"] as const) {
      expect(contaminationPolicy(material)).toBe("NOT_ASKED");
    }
  });

  it("dirige la mirada al interior en fibra", () => {
    expect(contaminationQuestion("BEVERAGE_CARTON")).toContain("dentro");
    expect(contaminationQuestion("CARDBOARD")).toContain("dentro");
  });

  it("solo considera limpio el estado CLEAN", () => {
    expect(isContaminated("CLEAN")).toBe(false);
    for (const option of CONTAMINATION_OPTIONS.filter((entry) => entry.state !== "CLEAN")) {
      expect(isContaminated(option.state)).toBe(true);
    }
  });
});
