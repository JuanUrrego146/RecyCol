/**
 * Ruta de caneca para la pantalla de recompensa.
 *
 * **Lee el perfil normativo real del proyecto**, `shared/resources/profiles/co.json`,
 * en vez de copiar sus reglas aquí. Es deliberado: RNF-004 dice que un país es
 * un archivo JSON, y si esta plataforma repitiera las reglas en TypeScript,
 * cambiar la norma obligaría a tocar dos sitios y la recompensa acabaría
 * mintiéndole al aportante. El archivo es de ámbito RULES: **se lee, no se
 * toca**.
 *
 * Esto no es adorno. Es lo que convierte la captura en algo que la gente repite:
 * después de etiquetar, la persona se entera de a qué caneca va su residuo y por
 * qué. La app principal de RecyCol, funcionando como recompensa.
 *
 * Regla de oro: **la respuesta se enseña siempre después de etiquetar, nunca
 * antes**. Enseñarla antes sesgaría la etiqueta, que es justo lo que este
 * proyecto vino a evitar.
 */

import profileJson from "../../../../shared/resources/profiles/co.json";
import type { ContaminationState } from "./contamination";
import { isContaminated } from "./contamination";
import type { Material } from "./materials";

export type BinRoute = "RECYCLABLE" | "NON_RECYCLABLE" | "ORGANIC" | "SPECIAL_COLLECTION";

interface BinDefinition {
  readonly id: string;
  readonly displayName: string;
  readonly colorHex: string;
  readonly route: string;
}

interface MaterialRule {
  readonly material: string;
  readonly targetBin: string;
  readonly contaminatedFallback: string | null;
  readonly justification: string;
}

interface NormativeProfile {
  readonly isoCode: string;
  readonly regulationName: string;
  readonly bins: readonly BinDefinition[];
  readonly rules: readonly MaterialRule[];
  readonly conservativeBin: string;
}

const PROFILE = profileJson as unknown as NormativeProfile;

export const REGULATION_NAME = PROFILE.regulationName;

export interface BinDecision {
  readonly binId: string;
  readonly displayName: string;
  readonly colorHex: string;
  readonly route: BinRoute;
  readonly justification: string;
  /** `true` si la contaminación declarada degradó la decisión respecto al destino ideal. */
  readonly degraded: boolean;
}

function binById(id: string): BinDefinition {
  const bin = PROFILE.bins.find((candidate) => candidate.id === id);
  if (!bin) {
    throw new Error(`El perfil ${PROFILE.isoCode} no define la caneca "${id}"`);
  }
  return bin;
}

/**
 * Resuelve la caneca de un material dado su estado de contaminación declarado.
 *
 * La degradación por contaminación vive en el perfil (`contaminatedFallback`),
 * no aquí: este módulo la aplica, no la decide.
 */
export function resolveBin(
  material: Material,
  contamination: ContaminationState | null,
): BinDecision {
  const rule = PROFILE.rules.find((candidate) => candidate.material === material);
  if (!rule) {
    throw new Error(`El perfil ${PROFILE.isoCode} no define regla para ${material}`);
  }

  const degraded =
    contamination !== null && isContaminated(contamination) && rule.contaminatedFallback !== null;
  const bin = binById(degraded ? rule.contaminatedFallback! : rule.targetBin);

  return {
    binId: bin.id,
    displayName: bin.displayName,
    colorHex: bin.colorHex,
    route: bin.route as BinRoute,
    justification: rule.justification,
    degraded,
  };
}
