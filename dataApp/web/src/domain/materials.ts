/**
 * Taxonomía cerrada de materiales — espejo de `WasteMaterial` del dominio Kotlin
 * y de `target_taxonomy.materials` en `ml/taxonomy/label_mapping.yaml`.
 *
 * Es **lista cerrada a propósito**: CONTEXTO.md §10 («cómo evitar etiquetas
 * basura») descarta el texto libre como vía principal porque produce «botella»,
 * «botella de plástico», «plastico» y «PET» para el mismo objeto. Aquí el
 * aportante solo puede elegir una de estas once, así que la etiqueta ya nace
 * canónica y la ingesta de ML no tiene que mapear nada.
 *
 * Si alguna vez cambia el enum del dominio, este archivo cambia con él: la
 * prueba `materials.test.ts` compara contra la lista literal.
 */

export const MATERIALS = [
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
] as const;

export type Material = (typeof MATERIALS)[number];

export interface MaterialInfo {
  /** Nombre visible, en español. */
  readonly name: string;
  /** Glifo que acompaña siempre al nombre — nunca se identifica solo por color (RNF-010). */
  readonly glyph: string;
  /** Ejemplos concretos: son lo que de verdad desambigua al aportante. */
  readonly examples: string;
  /**
   * Pista de desambiguación para los pares que más se confunden. Solo se
   * escribe donde hay un error real y caro documentado en CONTEXTO.md §7.
   */
  readonly hint?: string;
}

export const MATERIAL_INFO: Readonly<Record<Material, MaterialInfo>> = {
  PLASTIC: {
    name: "Plástico",
    glyph: "🧴",
    examples: "Botella, envase, bolsa, tapa plástica",
  },
  PAPER: {
    name: "Papel",
    glyph: "📄",
    examples: "Hoja, periódico, revista, sobre",
    hint: "Si es rígido y con ondas por dentro, es cartón.",
  },
  CARDBOARD: {
    name: "Cartón",
    glyph: "📦",
    examples: "Caja de envío, caja de pizza, empaque corrugado",
    hint: "Si es un vaso o una caja de líquidos con brillo por dentro, es cartón de bebidas.",
  },
  BEVERAGE_CARTON: {
    name: "Cartón de bebidas",
    glyph: "🥤",
    examples: "Vaso de café, caja de leche o jugo, tetrapak",
    hint: "Parece cartón pero por dentro tiene una capa plástica o metalizada que brilla.",
  },
  GLASS: {
    name: "Vidrio",
    glyph: "🫙",
    examples: "Botella, frasco, tarro",
  },
  METAL: {
    name: "Metal",
    glyph: "🥫",
    examples: "Lata, tapa metálica, papel aluminio",
  },
  ORGANIC: {
    name: "Orgánico",
    glyph: "🍎",
    examples: "Cáscara, restos de comida, residuos de poda",
  },
  TEXTILE: {
    name: "Textil",
    glyph: "👕",
    examples: "Ropa, trapo, zapato, tela",
  },
  BATTERY: {
    name: "Pilas y baterías",
    glyph: "🔋",
    examples: "Pila AA, batería de celular, batería de reloj",
  },
  ELECTRONIC: {
    name: "Electrónicos",
    glyph: "🔌",
    examples: "Cargador, audífonos, celular, control remoto",
  },
  RESIDUAL: {
    name: "No aprovechable",
    glyph: "🗑️",
    examples: "Servilleta usada, icopor sucio, colilla, empaque metalizado de snack",
  },
};

export function isMaterial(value: string): value is Material {
  return (MATERIALS as readonly string[]).includes(value);
}

export function materialName(material: Material): string {
  return MATERIAL_INFO[material].name;
}
