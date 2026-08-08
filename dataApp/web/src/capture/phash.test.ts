import { describe, expect, it } from "vitest";
import {
  NEAR_DUPLICATE_DISTANCE,
  PHASH_IMAGE_SIZE,
  hammingDistance,
  isNearDuplicate,
  phashFromGrayscale,
} from "./phash";

/**
 * Patrón con estructura en varias frecuencias, parecido a lo que produce una
 * foto real.
 *
 * **No sirve una rampa lineal.** Una rampa concentra toda su energía en la
 * primera fila y la primera columna de la DCT, así que ~49 de los 64
 * coeficientes de baja frecuencia quedan en cero y la mediana cae sobre ruido de
 * coma flotante: los bits salen aleatorios y el hash deja de ser estable. Es una
 * propiedad del pHash, no un defecto de esta implementación, y por eso la imagen
 * de prueba tiene que tener contenido de verdad.
 */
function structured(brightness = 0): Float64Array {
  const gray = new Float64Array(PHASH_IMAGE_SIZE * PHASH_IMAGE_SIZE);
  for (let y = 0; y < PHASH_IMAGE_SIZE; y += 1) {
    for (let x = 0; x < PHASH_IMAGE_SIZE; x += 1) {
      const value =
        128 +
        50 * Math.sin(x / 3.1) +
        35 * Math.cos(y / 4.7) +
        20 * Math.sin((x + y) / 6.3) +
        brightness;
      gray[y * PHASH_IMAGE_SIZE + x] = Math.round(Math.min(255, Math.max(0, value)));
    }
  }
  return gray;
}

function noise(seed: number): Float64Array {
  // Congruencial lineal: hace falta ruido reproducible, no bueno.
  let state = seed;
  const gray = new Float64Array(PHASH_IMAGE_SIZE * PHASH_IMAGE_SIZE);
  for (let i = 0; i < gray.length; i += 1) {
    state = (state * 1103515245 + 12345) % 2147483648;
    gray[i] = state % 256;
  }
  return gray;
}

describe("pHash", () => {
  it("produce 64 bits en hexadecimal", () => {
    expect(phashFromGrayscale(structured())).toMatch(/^[0-9a-f]{16}$/);
  });

  it("es determinista", () => {
    expect(phashFromGrayscale(structured())).toBe(phashFromGrayscale(structured()));
  });

  it("da distancia cero a la misma imagen", () => {
    expect(hammingDistance(phashFromGrayscale(structured()), phashFromGrayscale(structured()))).toBe(0);
  });

  it("tolera un cambio pequeño de brillo", () => {
    // Subir el brillo de forma uniforme mueve el término DC y poco más; la
    // estructura de baja frecuencia, que es lo que el hash mide, no cambia. Es el
    // caso real de dos fotos del mismo objeto con exposición distinta.
    const distance = hammingDistance(
      phashFromGrayscale(structured()),
      phashFromGrayscale(structured(6)),
    );
    expect(distance).toBeLessThanOrEqual(NEAR_DUPLICATE_DISTANCE);
  });

  it("separa imágenes distintas", () => {
    const distance = hammingDistance(phashFromGrayscale(structured()), phashFromGrayscale(noise(7)));
    expect(distance).toBeGreaterThan(NEAR_DUPLICATE_DISTANCE);
    expect(isNearDuplicate(phashFromGrayscale(structured()), phashFromGrayscale(noise(7)))).toBe(false);
  });

  it("rechaza un gris del tamaño equivocado", () => {
    expect(() => phashFromGrayscale(new Float64Array(16))).toThrow();
  });

  it("exige hashes de la misma longitud al comparar", () => {
    expect(() => hammingDistance("00", "0000")).toThrow();
  });

  it("cuenta bien los bits distintos", () => {
    expect(hammingDistance("0000000000000000", "0000000000000001")).toBe(1);
    expect(hammingDistance("0000000000000000", "ffffffffffffffff")).toBe(64);
  });
});
