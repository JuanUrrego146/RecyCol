/**
 * Misiones — la app pide la clase que falta en vez de esperar a que llegue.
 *
 * CONTEXTO.md §10 dice que **importa más el equilibrio que el total**: «un tope
 * por clase en la app —dejar de pedir fotos de plástico cuando sobran— vale más
 * que duplicar el volumen». Este módulo es ese tope, y de paso resuelve el
 * problema de la etiqueta: cuando la app pide «un vaso de café», la persona ya
 * sabe qué está fotografiando antes de disparar. La intención precede a la foto,
 * así que la etiqueta nace limpia y sin sesgo de confirmación.
 *
 * Los objetivos salen literalmente de la tabla «volumen y balance que moverían
 * la aguja» de §10. No son estimaciones nuevas.
 */

import type { ContaminationState } from "./contamination";
import type { Material } from "./materials";
import { MATERIALS } from "./materials";

/** Preferencia de contaminación de una misión, para equilibrar limpio/sucio dentro de la clase. */
export type ContaminationPreference = "ANY" | "PREFER_CLEAN" | "PREFER_DIRTY";

export interface MissionTarget {
  readonly material: Material;
  /** Cuántas fotos aprobadas se necesitan de esta clase. */
  readonly target: number;
  /**
   * Prioridad: 1 es lo primero que hay que llenar. Refleja el orden de retorno
   * por hora de §10, no el orden del enum.
   */
  readonly tier: number;
  /**
   * Fracción del objetivo que debe estar contaminada. Solo en fibra: es donde
   * la etapa 2 murió por falta de datos reales y donde el plan B pregunta.
   * `null` en el resto, donde la contaminación no cambia la caneca.
   */
  readonly contaminatedShare: number | null;
  /** Por qué esta clase importa. Se enseña al aportante: motiva más que un número. */
  readonly reason: string;
}

export const MISSION_TARGETS: readonly MissionTarget[] = [
  {
    material: "BEVERAGE_CARTON",
    target: 400,
    tier: 1,
    contaminatedShare: 0.5,
    reason:
      "El vaso de café es el caso estrella de RecyCol y hoy solo hay ~100 fotos. Es lo que más falta.",
  },
  {
    material: "CARDBOARD",
    target: 500,
    tier: 2,
    contaminatedShare: 0.5,
    reason: "Con y sin grasa: es la única forma de que la app aprenda a ver cartón sucio de verdad.",
  },
  {
    material: "PAPER",
    target: 500,
    tier: 2,
    contaminatedShare: 0.5,
    reason: "Papel limpio y papel engrasado: la mancha en fibra no se puede simular.",
  },
  {
    material: "ELECTRONIC",
    target: 400,
    tier: 3,
    contaminatedShare: null,
    reason: "Ningún banco de imágenes público tiene aparatos eléctricos. Esta clase está a cero.",
  },
  {
    material: "RESIDUAL",
    target: 500,
    tier: 4,
    contaminatedShare: null,
    reason: "Es hoy la clase más débil del modelo: acierta menos de 3 de cada 100.",
  },
  {
    material: "BATTERY",
    target: 400,
    tier: 4,
    contaminatedShare: null,
    reason: "Van a punto posconsumo, no a caneca, y no hay control real que lo verifique.",
  },
  {
    material: "PLASTIC",
    target: 500,
    tier: 5,
    contaminatedShare: null,
    reason: "Fotos de móvil sobre basura real, no de estudio.",
  },
  {
    material: "GLASS",
    target: 500,
    tier: 5,
    contaminatedShare: null,
    reason: "Fotos de móvil sobre basura real, no de estudio.",
  },
  {
    material: "METAL",
    target: 500,
    tier: 5,
    contaminatedShare: null,
    reason: "Fotos de móvil sobre basura real, no de estudio.",
  },
  {
    material: "ORGANIC",
    target: 500,
    tier: 5,
    contaminatedShare: null,
    reason: "Confundir cualquier cosa con orgánico es de los errores más caros.",
  },
  {
    material: "TEXTILE",
    target: 500,
    tier: 5,
    contaminatedShare: null,
    reason: "Fotos de móvil sobre basura real, no de estudio.",
  },
];

/** Recuento de lo ya aportado y aprobado en una clase. */
export interface MaterialTally {
  readonly total: number;
  readonly contaminated: number;
}

export type Tally = Readonly<Partial<Record<Material, MaterialTally>>>;

export interface Mission {
  readonly material: Material;
  readonly target: number;
  readonly collected: number;
  readonly reason: string;
  readonly preference: ContaminationPreference;
}

export const EMPTY_TALLY: MaterialTally = { total: 0, contaminated: 0 };

function tallyOf(tally: Tally, material: Material): MaterialTally {
  return tally[material] ?? EMPTY_TALLY;
}

/**
 * Preferencia de contaminación dentro de una clase de fibra: si ya sobran fotos
 * limpias se piden sucias, y al revés. Fuera de la fibra siempre `ANY`.
 */
function preferenceFor(target: MissionTarget, counts: MaterialTally): ContaminationPreference {
  if (target.contaminatedShare === null || counts.total === 0) return "ANY";
  const wanted = target.contaminatedShare;
  const actual = counts.contaminated / counts.total;
  // Banda muerta del 10 %: sin ella la app cambiaría de petición cada foto.
  if (actual < wanted - 0.1) return "PREFER_DIRTY";
  if (actual > wanted + 0.1) return "PREFER_CLEAN";
  return "ANY";
}

/**
 * Elige qué pedir ahora.
 *
 * Recorre los tramos de prioridad en orden y, dentro del primero que aún tenga
 * clases incompletas, escoge la menos avanzada. `recent` evita pedir la misma
 * clase indefinidamente: si las últimas `REPEAT_LIMIT` peticiones fueron de esa
 * clase, cede el turno a la siguiente candidata. Sin eso la app pediría vasos de
 * café cuatrocientas veces seguidas y nadie volvería.
 *
 * Devuelve `null` cuando todos los objetivos están cubiertos.
 */
export const REPEAT_LIMIT = 4;

export function selectMission(tally: Tally, recent: readonly Material[] = []): Mission | null {
  const pending = MISSION_TARGETS.filter(
    (target) => tallyOf(tally, target.material).total < target.target,
  );
  if (pending.length === 0) return null;

  // Orden global: primero el tramo de prioridad, y dentro de él la clase menos
  // avanzada.
  const candidates = [...pending].sort(
    (a, b) => a.tier - b.tier || progressOf(tally, a) - progressOf(tally, b),
  );

  // Al agotar la racha se salta a la siguiente candidata **de la lista entera**,
  // no solo del mismo tramo. Si el tramo prioritario tiene una única clase —y el
  // primero lo tiene: cartón de bebidas—, ceder dentro del tramo no cedería
  // nunca, y la aplicación pediría vasos de café cuatrocientas veces seguidas.
  const streak = trailingStreak(recent);
  const rotated =
    streak !== null && streak.count >= REPEAT_LIMIT
      ? candidates.filter((target) => target.material !== streak.material)
      : candidates;

  // El respaldo solo entra en juego cuando esa clase es literalmente la única que
  // queda pendiente: mejor repetirla que no pedir nada.
  const chosen = rotated[0] ?? candidates[0];
  return chosen ? toMission(chosen, tallyOf(tally, chosen.material)) : null;
}

function toMission(target: MissionTarget, counts: MaterialTally): Mission {
  return {
    material: target.material,
    target: target.target,
    collected: counts.total,
    reason: target.reason,
    preference: preferenceFor(target, counts),
  };
}

function progressOf(tally: Tally, target: MissionTarget): number {
  return tallyOf(tally, target.material).total / target.target;
}

function trailingStreak(recent: readonly Material[]): { material: Material; count: number } | null {
  const last = recent[recent.length - 1];
  if (last === undefined) return null;
  let count = 0;
  for (let i = recent.length - 1; i >= 0 && recent[i] === last; i -= 1) count += 1;
  return { material: last, count };
}

/** Progreso global del proyecto, para la barra de la pantalla de inicio. */
export function overallProgress(tally: Tally): { collected: number; target: number; ratio: number } {
  const target = MISSION_TARGETS.reduce((sum, entry) => sum + entry.target, 0);
  const collected = MISSION_TARGETS.reduce(
    (sum, entry) => sum + Math.min(tallyOf(tally, entry.material).total, entry.target),
    0,
  );
  return { collected, target, ratio: target === 0 ? 0 : collected / target };
}

/** Objetivo declarado de una clase, o `null` si no está en la tabla de misiones. */
export function targetFor(material: Material): MissionTarget | null {
  return MISSION_TARGETS.find((entry) => entry.material === material) ?? null;
}

/** Verificación de integridad: la tabla de misiones cubre las once clases. */
export function missionTableCoversTaxonomy(): boolean {
  return MATERIALS.every((material) => MISSION_TARGETS.some((entry) => entry.material === material));
}

/** Texto de la petición, ya resuelto con la preferencia de contaminación. */
export function missionHeadline(mission: Mission, materialName: string): string {
  switch (mission.preference) {
    case "PREFER_DIRTY":
      return `Busca ${materialName.toLowerCase()} usado o sucio`;
    case "PREFER_CLEAN":
      return `Busca ${materialName.toLowerCase()} limpio`;
    case "ANY":
      return `Busca ${materialName.toLowerCase()}`;
  }
}

/** Recuento de contaminadas a partir de un estado declarado, para actualizar el tally local. */
export function countsAsContaminated(state: ContaminationState | null): boolean {
  return state !== null && state !== "CLEAN";
}
