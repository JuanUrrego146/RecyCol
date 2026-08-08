/**
 * Misiones — qué falta y en qué orden.
 *
 * CONTEXTO.md §10 dice que **importa más el equilibrio que el total**: «un tope
 * por clase en la app —dejar de pedir fotos de plástico cuando sobran— vale más
 * que duplicar el volumen». Este módulo es ese tope, y de paso resuelve el
 * problema de la etiqueta: quien elige una clase del menú antes de disparar ya
 * sabe qué está fotografiando. La intención precede a la foto, así que la
 * etiqueta nace limpia y sin sesgo de confirmación.
 *
 * Ordenar es cosa nuestra; elegir, de quien tiene la basura delante. `rankNeeds`
 * explica por qué esa división y no una misión impuesta.
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
  /** Cuántas faltan para el objetivo. Siempre mayor que cero en lo que devuelve `rankNeeds`. */
  readonly missing: number;
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
 * Todo lo que falta, ordenado por lo que más falta.
 *
 * Primero el tramo de prioridad y, dentro de él, la clase menos avanzada. Ese
 * orden es el mismo de siempre; lo que cambió es que se devuelve la lista entera
 * en vez de su primer elemento.
 *
 * **Por qué un menú y no una misión impuesta.** Una sola petición obliga a
 * adivinar qué tiene delante quien abre la aplicación, y adivina mal casi
 * siempre: si pide un vaso de café y la persona está frente a una caneca de
 * botellas, la respuesta útil —«tengo esto otro»— quedaba escondida detrás de un
 * botón secundario. Enseñando las cinco que más faltan, la persona empareja lo
 * que ve con lo que el proyecto necesita, y el equilibrio entre clases que pide
 * §10 se consigue igual: el orden sigue siendo nuestro, la elección es suya.
 * De paso desaparece la rotación anti-racha, que existía solo para que una misión
 * impuesta no pidiera vasos de café cuatrocientas veces seguidas.
 *
 * Devuelve la lista vacía cuando todos los objetivos están cubiertos.
 */
export function rankNeeds(tally: Tally): Mission[] {
  return MISSION_TARGETS.filter((target) => tallyOf(tally, target.material).total < target.target)
    .sort((a, b) => a.tier - b.tier || progressOf(tally, a) - progressOf(tally, b))
    .map((target) => toMission(target, tallyOf(tally, target.material)));
}

function toMission(target: MissionTarget, counts: MaterialTally): Mission {
  return {
    material: target.material,
    target: target.target,
    collected: counts.total,
    missing: Math.max(0, target.target - counts.total),
    reason: target.reason,
    preference: preferenceFor(target, counts),
  };
}

function progressOf(tally: Tally, target: MissionTarget): number {
  return tallyOf(tally, target.material).total / target.target;
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

/**
 * Matiz de contaminación de una fila del menú, o `null` si da igual.
 *
 * Es una coletilla y no un título porque el título de la fila es el material: en
 * una lista de cinco, cinco frases que empiezan por «Busca» no se distinguen de
 * un vistazo.
 */
export function preferenceHint(preference: ContaminationPreference): string | null {
  switch (preference) {
    case "PREFER_DIRTY":
      return "mejor usado o sucio";
    case "PREFER_CLEAN":
      return "mejor limpio";
    case "ANY":
      return null;
  }
}

/** Recuento de contaminadas a partir de un estado declarado, para actualizar el tally local. */
export function countsAsContaminated(state: ContaminationState | null): boolean {
  return state !== null && state !== "CLEAN";
}
