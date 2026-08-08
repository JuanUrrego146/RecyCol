/**
 * El motor de misiones es lo que equilibra el dataset. §10 dice que el balance
 * entre clases pesa más que el total, así que estas pruebas fijan el orden de
 * prioridad y el tope por clase.
 */

import { describe, expect, it } from "vitest";
import { MATERIALS } from "./materials";
import {
  MISSION_TARGETS,
  missionTableCoversTaxonomy,
  overallProgress,
  preferenceHint,
  rankNeeds,
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

describe("orden de lo que falta", () => {
  it("sin nada aportado enseña las once, con el cartón de bebidas primero", () => {
    const needs = rankNeeds({});
    expect(needs).toHaveLength(MISSION_TARGETS.length);
    expect(needs[0]?.material).toBe("BEVERAGE_CARTON");
  });

  it("saca de la lista la clase que llegó a su objetivo", () => {
    const needs = rankNeeds(full("BEVERAGE_CARTON"));
    expect(needs.map((need) => need.material)).not.toContain("BEVERAGE_CARTON");
    expect(["CARDBOARD", "PAPER"]).toContain(needs[0]?.material);
  });

  it("ordena por tramo de prioridad y, dentro del tramo, por avance", () => {
    const tally: Tally = {
      BEVERAGE_CARTON: { total: 400, contaminated: 200 },
      CARDBOARD: { total: 300, contaminated: 150 },
      PAPER: { total: 10, contaminated: 5 },
    };
    const materials = rankNeeds(tally).map((need) => need.material);
    expect(materials[0]).toBe("PAPER");
    expect(materials[1]).toBe("CARDBOARD");
    // El tramo 2 entero va antes que cualquier cosa del tramo 3 en adelante.
    expect(materials.indexOf("CARDBOARD")).toBeLessThan(materials.indexOf("ELECTRONIC"));
  });

  it("nunca lista una clase sin nada que falte", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100_000, contaminated: 0 } };
    for (const need of rankNeeds(tally)) {
      expect(need.missing).toBeGreaterThan(0);
      expect(need.missing).toBe(need.target - need.collected);
    }
  });

  it("pide sucio cuando sobra limpio en una clase de fibra", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100, contaminated: 5 } };
    expect(rankNeeds(tally)[0]?.preference).toBe("PREFER_DIRTY");
  });

  it("pide limpio cuando sobra sucio", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100, contaminated: 95 } };
    expect(rankNeeds(tally)[0]?.preference).toBe("PREFER_CLEAN");
  });

  it("no expresa preferencia cuando el reparto ya está equilibrado", () => {
    const tally: Tally = { BEVERAGE_CARTON: { total: 100, contaminated: 50 } };
    expect(rankNeeds(tally)[0]?.preference).toBe("ANY");
    expect(preferenceHint("ANY")).toBeNull();
  });

  it("nunca pide contaminación fuera de la fibra", () => {
    const tally = allFull();
    delete (tally as Record<string, unknown>).PLASTIC;
    const needs = rankNeeds(tally);
    expect(needs).toHaveLength(1);
    expect(needs[0]?.material).toBe("PLASTIC");
    expect(needs[0]?.preference).toBe("ANY");
  });

  it("devuelve la lista vacía cuando todo está cubierto", () => {
    expect(rankNeeds(allFull())).toEqual([]);
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
