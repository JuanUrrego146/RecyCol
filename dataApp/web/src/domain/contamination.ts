/**
 * Estado de contaminación — el campo de **máxima prioridad** de la captura
 * (CONTEXTO.md §10).
 *
 * Por qué existe: la etapa 2 automática de contaminación se entrenó con pares
 * sintéticos, dio 94 % en sintético y declaró limpio el 98,75 % de RealWaste
 * (§7, S26). No transfiere. La única salida es contaminación **real etiquetada
 * por una persona**, y no existe en ninguna fuente pública.
 *
 * Por qué en fibra sobre todo: con el plan B (decisión 9 de §6) la app solo
 * pregunta por cartón y papel, porque ahí la contaminación es irreversible —
 * la fibra **absorbe** la grasa y el líquido, no los lleva superpuestos. Un
 * plástico sucio se enjuaga y se recicla igual, así que la etiqueta no cambia
 * ninguna decisión.
 */

import type { Material } from "./materials";

export const CONTAMINATION_STATES = ["CLEAN", "RESIDUE", "LIQUID", "GREASE"] as const;

export type ContaminationState = (typeof CONTAMINATION_STATES)[number];

/** Cuánto insiste la interfaz en preguntar por el estado de contaminación. */
export type ContaminationPolicy =
  /** No se puede continuar sin responder. Solo fibra: es la captura prioritaria. */
  | "REQUIRED"
  /** Se pregunta, pero se puede omitir con un toque. */
  | "OPTIONAL"
  /** Ni se pregunta: la respuesta no cambiaría la caneca ni aporta al modelo. */
  | "NOT_ASKED";

/**
 * Obligatorio en las tres clases de fibra celulósica. `BEVERAGE_CARTON` entra
 * aunque el plan B no lo pregunte en la app principal: es el caso estrella del
 * producto, hoy sin control de dominio real, y su regla de negocio depende
 * enteramente de si tiene líquido dentro (decisión 3 de §6).
 */
const REQUIRED_FOR: readonly Material[] = ["PAPER", "CARDBOARD", "BEVERAGE_CARTON"];

/**
 * Opcional donde la contaminación no cambia la ruta pero sí explica errores del
 * modelo: la carpeta `trash` de Garbage v2 enseñó «envase degradado ⇒ RESIDUAL»
 * (§7), así que saber si un envase estaba sucio permite medir ese sesgo.
 */
const OPTIONAL_FOR: readonly Material[] = ["PLASTIC", "GLASS", "METAL"];

export function contaminationPolicy(material: Material): ContaminationPolicy {
  if (REQUIRED_FOR.includes(material)) return "REQUIRED";
  if (OPTIONAL_FOR.includes(material)) return "OPTIONAL";
  return "NOT_ASKED";
}

export interface ContaminationOption {
  readonly state: ContaminationState;
  readonly name: string;
  readonly glyph: string;
}

export const CONTAMINATION_OPTIONS: readonly ContaminationOption[] = [
  { state: "CLEAN", name: "Limpio y seco", glyph: "✨" },
  { state: "RESIDUE", name: "Con restos sólidos", glyph: "🍚" },
  { state: "LIQUID", name: "Con líquido", glyph: "💧" },
  { state: "GREASE", name: "Con grasa", glyph: "🧈" },
];

/**
 * Pregunta específica por material. En cartón de bebidas y cartón hay regla de
 * inspección interior en el perfil colombiano (`inspectionRules`), así que la
 * pregunta dirige la mirada adentro en vez de quedarse en la superficie.
 */
export function contaminationQuestion(material: Material): string {
  switch (material) {
    case "BEVERAGE_CARTON":
      return "Mira por dentro: ¿queda bebida o residuo?";
    case "CARDBOARD":
      return "Mira por dentro: ¿tiene grasa, salsa o restos?";
    case "PAPER":
      return "¿Está limpio y seco, o engrasado o húmedo?";
    default:
      return "¿Cómo está por dentro?";
  }
}

export function isContaminated(state: ContaminationState): boolean {
  return state !== "CLEAN";
}
