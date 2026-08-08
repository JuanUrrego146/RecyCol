/**
 * Filtro de calidad de frame — port en TypeScript del filtro de producción.
 *
 * Fuentes de verdad, en este orden:
 *   1. `androidApp/camera/.../FrameQualityThresholds.kt` (S11) — la app real.
 *   2. `ml/quality/frame_quality_gate.py` — la réplica en NumPy que ya usa el
 *      pipeline de ML (coordinación #21, CAM→ML).
 * Este archivo es la tercera réplica, la del navegador. **Los umbrales no se
 * inventan aquí**: si S39/QA los recalibra, hay que sincronizar los tres y
 * anotar la versión.
 *
 * Para qué sirve en esta plataforma: cada foto aportada llega con las mismas
 * métricas con las que la app decide si clasifica un frame o pide otra toma. Eso
 * permite a ML separar «el modelo falla en frames que la app aceptaría» —brecha
 * real— de «falla en frames que el filtro habría rechazado» —fuera del dominio
 * operativo—, que es exactamente la distinción que §10 pide poder hacer.
 *
 * Y sirve para algo más inmediato: si la foto no pasaría el filtro, la app se lo
 * dice al aportante en el momento y le ofrece repetir, en vez de acumular fotos
 * borrosas que la moderación tendrá que descartar una a una.
 */

/** Espejo de `FrameQualityThresholds.kt` (main@cb8405b). */
export const SHARPNESS_SATURATION = 900.0;
export const BLURRY_BELOW = 0.18;
export const UNDEREXPOSED_BELOW = 0.16;
export const OVEREXPOSED_ABOVE = 0.92;
export const SAMPLE_STEP = 2;

/** Ponderación BT.601: la misma con la que el sensor produce el plano Y que ve la app. */
const BT601_R = 0.299;
const BT601_G = 0.587;
const BT601_B = 0.114;

export interface FrameMetrics {
  readonly sharpness: number;
  readonly luminance: number;
  readonly blurry: boolean;
  readonly underexposed: boolean;
  readonly overexposed: boolean;
}

export interface LumaPlane {
  readonly data: Uint8ClampedArray;
  readonly width: number;
  readonly height: number;
}

/**
 * Convierte RGBA (el formato de `ImageData`) al plano de luminancia, igual que
 * `luma_from_rgb` en la réplica de NumPy. El canal alfa se ignora: las capturas
 * de cámara son opacas.
 */
export function lumaFromRgba(rgba: Uint8ClampedArray, width: number, height: number): LumaPlane {
  const data = new Uint8ClampedArray(width * height);
  for (let i = 0, p = 0; p < data.length; i += 4, p += 1) {
    const value = BT601_R * rgba[i]! + BT601_G * rgba[i + 1]! + BT601_B * rgba[i + 2]!;
    data[p] = Math.round(value);
  }
  return { data, width, height };
}

/**
 * Varianza del laplaciano de 4 vecinos sobre rejilla submuestreada, normalizada
 * y saturada en `SHARPNESS_SATURATION`.
 *
 * La rejilla es la misma que en Kotlin y NumPy: centros en `[s, dim - s)` con
 * paso `s`, y vecinos a distancia `s`. Recorrer la imagen entera daría otro
 * número y rompería la paridad.
 */
export function sharpnessOf(luma: LumaPlane): number {
  const s = SAMPLE_STEP;
  const { data, width, height } = luma;

  let count = 0;
  let sum = 0;
  let sumSquares = 0;

  for (let y = s; y < height - s; y += s) {
    const row = y * width;
    const rowUp = (y - s) * width;
    const rowDown = (y + s) * width;
    for (let x = s; x < width - s; x += s) {
      const response =
        4 * data[row + x]! -
        data[row + x - s]! -
        data[row + x + s]! -
        data[rowUp + x]! -
        data[rowDown + x]!;
      count += 1;
      sum += response;
      sumSquares += response * response;
    }
  }

  if (count === 0) return 0;
  const mean = sum / count;
  const variance = sumSquares / count - mean * mean;
  return Math.min(1, Math.max(0, variance / SHARPNESS_SATURATION));
}

/** Luminancia media submuestreada, en `[0,1]`. */
export function meanLuminanceOf(luma: LumaPlane): number {
  const s = SAMPLE_STEP;
  const { data, width, height } = luma;
  let count = 0;
  let sum = 0;
  for (let y = 0; y < height; y += s) {
    const row = y * width;
    for (let x = 0; x < width; x += s) {
      sum += data[row + x]!;
      count += 1;
    }
  }
  return count === 0 ? 0 : sum / count / 255;
}

export function metricsOf(luma: LumaPlane): FrameMetrics {
  const sharpness = sharpnessOf(luma);
  const luminance = meanLuminanceOf(luma);
  return {
    sharpness,
    luminance,
    blurry: sharpness < BLURRY_BELOW,
    underexposed: luminance < UNDEREXPOSED_BELOW,
    overexposed: luminance > OVEREXPOSED_ABOVE,
  };
}

/** `true` si la app de producción clasificaría este frame en vez de pedir otra toma. */
export function accepts(metrics: FrameMetrics): boolean {
  return !metrics.blurry && !metrics.underexposed && !metrics.overexposed;
}

/**
 * Motivo del rechazo en lenguaje de persona, o `null` si la foto pasa.
 *
 * La exposición se comprueba **antes** que el desenfoque, y no es un capricho de
 * redacción: un fotograma oscuro no tiene gradiente medible, así que sale borroso
 * *además de* oscuro. Decirle a alguien que le tembló el pulso cuando lo que pasa
 * es que no hay luz le hace repetir la foto igual de mal.
 */
export function rejectionReason(metrics: FrameMetrics): string | null {
  if (metrics.underexposed) return "Hay muy poca luz para distinguir el objeto.";
  if (metrics.overexposed) return "La foto está quemada de luz.";
  if (metrics.blurry) return "La foto salió movida o desenfocada.";
  return null;
}
