/**
 * pHash perceptual de 64 bits, compatible en formato con `imagehash.phash`.
 *
 * Para qué: §10, punto 4 — «deduplicar contra todo lo existente con el pHash que
 * ya usa S22, **incluido contra RealWaste**: si alguien fotografía algo
 * casualmente idéntico al control, fuera». Contaminar el control es la forma más
 * rápida de destruir la única evidencia de generalización que tiene el proyecto.
 *
 * Reparto de responsabilidades, y conviene tenerlo claro:
 *
 * - **Aquí (navegador)**: hash barato para detectar reenvíos del mismo aporte y
 *   ráfagas casi idénticas del mismo objeto. Se guarda con la captura.
 * - **En `ml/` (S22)**: la deduplicación **autoritativa** contra el pool y contra
 *   RealWaste, con la misma herramienta que ya usa el pipeline.
 *
 * Por qué no basta con el de aquí: el redimensionado del navegador
 * (`drawImage` con suavizado bilineal) no es el de Pillow (Lanczos), así que
 * unos pocos bits pueden diferir sobre la misma imagen. Con distancia de Hamming
 * eso es tolerable para un prefiltro, y **no lo es** para la decisión de dejar
 * fuera una imagen del control. Esa decisión se toma en el pipeline de ML.
 *
 * Algoritmo (`hash_size = 8`, `highfreq_factor = 4`): gris 32×32 → DCT-II 2D sin
 * normalizar → submatriz 8×8 de baja frecuencia → bit a 1 donde el coeficiente
 * supera la mediana de esos 64 (la mediana incluye el término DC, igual que
 * `imagehash`).
 *
 * **Límite conocido, común a todo pHash**: sobre una imagen sin estructura —una
 * pared lisa, una rampa de luz uniforme— casi todos los coeficientes de baja
 * frecuencia valen cero, la mediana cae sobre ruido numérico y el hash deja de
 * ser estable entre tomas casi idénticas. En la práctica no molesta: el filtro de
 * calidad ya rechaza esos fotogramas, y fallar hacia «no son duplicados» solo
 * deja trabajo a la deduplicación de ML, que es la que manda.
 */

const HASH_SIZE = 8;
const IMAGE_SIZE = HASH_SIZE * 4;

/** Tabla de cosenos precalculada: la DCT se ejecuta por cada foto y esto la abarata. */
const COSINE_TABLE = buildCosineTable(IMAGE_SIZE);

function buildCosineTable(size: number): Float64Array {
  const table = new Float64Array(size * size);
  for (let k = 0; k < size; k += 1) {
    for (let n = 0; n < size; n += 1) {
      table[k * size + n] = Math.cos((Math.PI * k * (2 * n + 1)) / (2 * size));
    }
  }
  return table;
}

/** DCT-II sin normalizar, la de `scipy.fftpack.dct` con los valores por defecto. */
function dct1d(input: Float64Array, output: Float64Array, size: number): void {
  for (let k = 0; k < size; k += 1) {
    let sum = 0;
    for (let n = 0; n < size; n += 1) {
      sum += input[n]! * COSINE_TABLE[k * size + n]!;
    }
    output[k] = 2 * sum;
  }
}

/**
 * Calcula el hash a partir de un gris de `IMAGE_SIZE`×`IMAGE_SIZE`.
 * Se expone aparte para poder probarlo sin canvas.
 */
export function phashFromGrayscale(gray: Float64Array): string {
  if (gray.length !== IMAGE_SIZE * IMAGE_SIZE) {
    throw new Error(`Se esperaba un gris de ${IMAGE_SIZE}×${IMAGE_SIZE}`);
  }

  const columns = new Float64Array(IMAGE_SIZE * IMAGE_SIZE);
  const row = new Float64Array(IMAGE_SIZE);
  const transformed = new Float64Array(IMAGE_SIZE);

  // DCT por columnas (axis=0), igual que imagehash.
  for (let x = 0; x < IMAGE_SIZE; x += 1) {
    for (let y = 0; y < IMAGE_SIZE; y += 1) row[y] = gray[y * IMAGE_SIZE + x]!;
    dct1d(row, transformed, IMAGE_SIZE);
    for (let y = 0; y < IMAGE_SIZE; y += 1) columns[y * IMAGE_SIZE + x] = transformed[y]!;
  }

  // DCT por filas (axis=1). Solo se necesitan las HASH_SIZE primeras filas.
  const low = new Float64Array(HASH_SIZE * HASH_SIZE);
  for (let y = 0; y < HASH_SIZE; y += 1) {
    for (let x = 0; x < IMAGE_SIZE; x += 1) row[x] = columns[y * IMAGE_SIZE + x]!;
    dct1d(row, transformed, IMAGE_SIZE);
    for (let x = 0; x < HASH_SIZE; x += 1) low[y * HASH_SIZE + x] = transformed[x]!;
  }

  const median = medianOf(low);
  let hex = "";
  for (let byte = 0; byte < 8; byte += 1) {
    let value = 0;
    for (let bit = 0; bit < 8; bit += 1) {
      value = (value << 1) | (low[byte * 8 + bit]! > median ? 1 : 0);
    }
    hex += value.toString(16).padStart(2, "0");
  }
  return hex;
}

function medianOf(values: Float64Array): number {
  const sorted = Array.from(values).sort((a, b) => a - b);
  const middle = sorted.length / 2;
  return (sorted[middle - 1]! + sorted[middle]!) / 2;
}

/** Distancia de Hamming entre dos hashes hexadecimales de 64 bits. */
export function hammingDistance(left: string, right: string): number {
  if (left.length !== right.length) {
    throw new Error("Los hashes deben tener la misma longitud");
  }
  let distance = 0;
  for (let i = 0; i < left.length; i += 1) {
    let diff = parseInt(left[i]!, 16) ^ parseInt(right[i]!, 16);
    while (diff !== 0) {
      distance += diff & 1;
      diff >>= 1;
    }
  }
  return distance;
}

/**
 * Umbral de «casi la misma foto». 5 bits de 64 es el valor habitual para pHash
 * de 8×8: por debajo hay reencuadres y cambios de exposición de la misma escena.
 */
export const NEAR_DUPLICATE_DISTANCE = 5;

export function isNearDuplicate(left: string, right: string): boolean {
  return hammingDistance(left, right) <= NEAR_DUPLICATE_DISTANCE;
}

export { IMAGE_SIZE as PHASH_IMAGE_SIZE };
