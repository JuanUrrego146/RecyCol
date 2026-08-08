/**
 * El motor de misiones es lo que equilibra el dataset. §10 dice que el balance
 * entre clases pesa más que el total, así que estas pruebas fijan el orden de
 * prioridad, el tope por clase y la alternancia.
 */

import { describe, expect, it } from "vitest";
import { MATERIALS } from "./materials";
import {
  MISSION_TARGETS,
  REPEAT_LIMIT,
  missionTableCoversTaxonomy,
  overallProgress,
  selectMission,
  targetFor,
  type Tally,
} from "./missions";

function full(material: string): Tally {
  const target = MISSION_TARGETS.find((entry) => entry.material === material)!;
  return { [target.material]: { total: target.target, contaminated: 0 } } as Tally;
}

function allFull(): Tally {
  const tally: Record<string, { total: number; contaminated: number }> = {};
  for (const entry of MISSION_TARGETS) {
    tally[entry.material] = { total: entry.target, contaminated: 0 };
  }
  return tally as Tally;
}

describe("tabla de misiones", () => {
  it("cubre las once clases de la taxonomía", () => {
    expect(missionTableCoversTaxonomy()).toBe(true);
    expect(MISSION_TARGETS).toHaveLength(MATERIALS.length);
  });

  it("pone el cartón de bebidas primero", () => {
    // Es el caso estrella del producto, tiene ~100 imágenes y es el mejor
    // retorno por hora de todo el proyecto (§8 y §10).
    expect(targetFor("BEVERAGE_CARTON")?.tier).toBe(1);
    const otherTiers = MISSION_TARGETS.filter((entry) => entry.material !== "BEVERAGE_CARTON").map(
      (entry) => entry.tier,
    );
    expect(Math.min(...otherTiers)).toBeGreaterThan(1);
  });

  it("solo pide equilibrio de contaminación en fibra", () => {
    const withShare = MISSION_TARGETS.filter((entry) => entry.contaminatedShare !== null).map(
      (entry) => entry.material,
    );
    expect(new Set(withShare)).toEqual(new Set(["BEVERAGE_CARTON", "CARDBOARD", "PAPER"]));
  });
});

describe("selección de misión", () => {
  it("empieza por el cartón de bebidas cuando no hay nada", () => {
    expect(selectMission({})?.material).toBe("BEVERAGE_CARTON");
  });

  it("deja de pedir una clase cuando llega a su objetivo", () => {
    const mission = selectMission(full("BEVERAGE_CARTON"));
    expect(mission?.material).not.toBe("BEVERAGE_CARTON");
    expect(["CARDBOARD", "PAPER"]).toContain(mission?.material);
  });

  it("dentro del mismo tramo elige la clase menos avanzada", () => {
    const tally: Tally = {
      BEVERAGE_CARTON: { total: 400, contaminated: 200 },
      CARDBOARD: { total: 300, contaminated: 150 },
      PAPER: { total: 10, contaminated: 5 },
    };
    expect(selectMission(tally)?.material).toBe("PAPER");
  });

  it("cede el turno tras varias peticiones seguidas de lo mismo", () => {
    const recent = Array.from({ length: REPEAT_LIMIT }, () => "BEVERAGE_CARTON" as const);
    const mission = selectMission({}, recent);
    expect(mission?.material).not.toBe("BEVERAGE_CARTON");
  });

  it("no cede antes de llegar al límite de repeticiones", () => {
    const recent = Array.from({ length: REPEAT_LIMIT - 1 }, () => "BEVERAGE_CARTON" as const);
    expect(selectMission({}, recent)?.material).toBe("BEVERAGE_CARTON");
  });

  it("pide sucio cuando sobra limpio en una clase de fibra", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100, contaminated: 5 } };
    expect(selectMission(tally)?.preference).toBe("PREFER_DIRTY");
  });

  it("pide limpio cuando sobra sucio", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100, contaminated: 95 } };
    expect(selectMission(tally)?.preference).toBe("PREFER_CLEAN");
  });

  it("no expresa preferencia cuando el reparto ya está equilibrado", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100, contaminated: 50 } };
    expect(selectMission(tally)?.preference).toBe("ANY");
  });

  it("nunca pide contaminación fuera de la fibra", () => {
    const tally = allFull();
    delete (tally as Record<string, unknown>).PLASTIC;
    expect(selectMission(tally)?.material).toBe("PLASTIC");
    expect(selectMission(tally)?.preference).toBe("ANY");
  });

  it("devuelve null cuando todo está cubierto", () => {
    expect(selectMission(allFull())).toBeNull();
  });
});

describe("progreso global", () => {
  it("es cero sin aportes y uno con todo cubierto", () => {
    expect(overallProgress({}).ratio).toBe(0);
    expect(overallProgress(allFull()).ratio).toBe(1);
  });

  it("no deja que una clase pasada de rosca infle el total", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100_000, contaminated: 0 } };
    const progress = overallProgress(tally);
    expect(progress.collected).toBe(targetFor("BEVERAGE_CARTON")!.target);
    expect(progress.ratio).toBeLessThan(1);
  });
});
