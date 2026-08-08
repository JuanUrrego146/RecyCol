/**
 * Los umbrales del navegador contra los de la app, leídos del Kotlin real.
 *
 * `qualityGate.ts` es una **copia manual de constantes**. Mientras coincidan,
 * la columna `quality_accepted` del manifiesto significa lo que dice significar:
 * «la app de producción habría clasificado esta foto». En cuanto QA recalibre el
 * filtro —ya pasó una vez, en S41— la copia se queda vieja **sin que nada falle**,
 * y el manifiesto empieza a mentirle al pipeline de ML sobre el dominio operativo.
 *
 * Esta prueba lee `FrameQualityThresholds.kt` y compara. Es el único vínculo que
 * existe entre las dos: no hay compilador que lo haga, porque son dos lenguajes y
 * dos despliegues distintos.
 *
 * Si se pone roja, **no la ajustes a ojo**: mira qué cambió en Kotlin, decide si
 * el cambio aplica aquí, y sincroniza también `ml/quality/frame_quality_gate.py`,
 * que es la tercera réplica.
 */

import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  BLURRY_BELOW,
  OVEREXPOSED_ABOVE,
  SHARPNESS_SATURATION,
  UNDEREXPOSED_BELOW,
} from "./qualityGate";

/**
 * Relativa al directorio de trabajo, que Vitest fija en la raíz del paquete
 * (`dataApp/web`). No se usa `__dirname` porque no existe en un módulo ESM.
 */
const KOTLIN =
  "../../androidApp/src/main/kotlin/com/recycol/android/camera/FrameQualityThresholds.kt";

/**
 * Lee `const val NOMBRE = 1.23f` y devuelve el número.
 *
 * A mano y sin expresiones regulares: son cuatro líneas de un archivo con formato
 * fijo, y una regex aquí se equivoca en silencio —devuelve `null` y la prueba
 * falla por el motivo equivocado— en vez de decir qué línea no supo leer.
 */
function constanteKotlin(fuente: string, nombre: string): number {
  const marca = `const val ${nombre}`;
  const linea = fuente.split("\n").find((l) => l.includes(marca));
  if (!linea) throw new Error(`No se encontró "${marca}" en FrameQualityThresholds.kt`);

  const despuesDelIgual = linea.slice(linea.indexOf("=", linea.indexOf(marca)) + 1);
  // Se quitan el sufijo de tipo de Kotlin (f/F/d/D), los separadores de millares
  // y cualquier comentario al final de la línea.
  const crudo = despuesDelIgual.split("//")[0]!.trim().replace(/_/g, "").replace(/[fFdD]$/, "");
  const valor = Number(crudo);
  if (!Number.isFinite(valor)) {
    throw new Error(`No se pudo leer el número de "${linea.trim()}" (quedó "${crudo}")`);
  }
  return valor;
}

describe("paridad de umbrales con la app Android", () => {
  // La carpeta androidApp es de otro ámbito y podría no estar en un checkout
  // parcial. Mejor avisar que fallar por algo que no es el objeto de la prueba.
  const disponible = existsSync(KOTLIN);
  const fuente = disponible ? readFileSync(KOTLIN, "utf8") : "";

  it.runIf(disponible)("la saturación de nitidez coincide", () => {
    expect(SHARPNESS_SATURATION).toBe(constanteKotlin(fuente, "SHARPNESS_SATURATION_VARIANCE"));
  });

  it.runIf(disponible)("el umbral de borroso coincide", () => {
    expect(BLURRY_BELOW).toBe(constanteKotlin(fuente, "BLURRY_BELOW"));
  });

  it.runIf(disponible)("el umbral de subexposición coincide", () => {
    expect(UNDEREXPOSED_BELOW).toBe(constanteKotlin(fuente, "UNDEREXPOSED_BELOW"));
  });

  it.runIf(disponible)("el umbral de sobreexposición coincide", () => {
    expect(OVEREXPOSED_ABOVE).toBe(constanteKotlin(fuente, "OVEREXPOSED_ABOVE"));
  });

  it("avisa si el archivo de Kotlin se movió", () => {
    // Que la ruta deje de existir no puede pasar en silencio: sin ella las
    // cuatro comprobaciones de arriba se saltan y la deriva vuelve a ser
    // invisible, que es justo lo que esta prueba viene a impedir.
    expect(
      disponible,
      `No se encontró ${KOTLIN}. Si androidApp se movió, actualiza la ruta; ` +
        "si no, este checkout no incluye la app y las comprobaciones se saltaron.",
    ).toBe(true);
  });
});
