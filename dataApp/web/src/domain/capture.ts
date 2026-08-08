/**
 * El registro de una captura: qué se guarda de cada foto y por qué.
 *
 * Cada campo de aquí sale de la tabla «qué capturar además de la foto y la
 * etiqueta» de CONTEXTO.md §10. La regla que la ordena: *una foto con etiqueta
 * sirve para entrenar material; sin lo demás se pierde la mitad del valor y no
 * se puede diagnosticar nada cuando el modelo falle*.
 *
 * Y una ausencia deliberada: **no hay geolocalización**. Ni campo, ni permiso,
 * ni petición al navegador. §10 la descarta por riesgo de privacidad sin retorno
 * técnico, y el país ya se conoce por el perfil activo.
 */

import type { ContaminationState } from "./contamination";
import type { Material } from "./materials";

/** Versión del esquema de captura. Sube si cambia la forma del registro. */
export const CAPTURE_SCHEMA_VERSION = 1;

export const LIGHT_CONDITIONS = ["INDOOR", "OUTDOOR", "LOW_LIGHT", "BACKLIT"] as const;
export type LightCondition = (typeof LIGHT_CONDITIONS)[number];

export const ANGLES = ["TOP_DOWN", "OBLIQUE", "SIDE"] as const;
export type Angle = (typeof ANGLES)[number];

export const PHYSICAL_STATES = ["INTACT", "DEFORMED", "BROKEN"] as const;
export type PhysicalState = (typeof PHYSICAL_STATES)[number];

export const BACKGROUNDS = ["BIN", "GROUND", "TABLE", "BAG", "OTHER"] as const;
export type Background = (typeof BACKGROUNDS)[number];

export type CaptureMode = "MISSION" | "FREE";

/** Estado en la cuarentena. Ninguna imagen entra al pool de ML sin pasar por revisión (§10, punto 5). */
export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED";

/**
 * Partición asignada en la revisión. `CONTROL` es el segundo conjunto de control
 * propio que §10 pide reservar **desde el primer día** y que jamás entrena;
 * `TRAIN` es todo lo demás. Se asigna por aportante, nunca por imagen.
 */
export type DataSplit = "TRAIN" | "CONTROL";

export interface QualityMetrics {
  /** Varianza del laplaciano normalizada, [0,1]. Misma métrica que la app real. */
  readonly sharpness: number;
  /** Luminancia media, [0,1]. */
  readonly luminance: number;
  /** `true` si la app de producción habría clasificado este frame en vez de pedir otra toma. */
  readonly accepted: boolean;
}

export interface ImageInfo {
  readonly width: number;
  readonly height: number;
  readonly bytes: number;
  readonly mimeType: string;
}

/**
 * Datos del dispositivo, para diagnosticar sesgo por cámara (§10, prioridad 🟡).
 * Solo lo que el navegador expone sin permisos y sin identificar a nadie: nada
 * de huella digital de dispositivo.
 */
export interface DeviceInfo {
  readonly platform: string;
  readonly screenWidth: number;
  readonly screenHeight: number;
  readonly pixelRatio: number;
  /** `navigator.deviceMemory` en GB, si el navegador lo expone. Proxy de gama. */
  readonly memoryGb: number | null;
  readonly cores: number | null;
}

/**
 * Recorte aplicado sobre el fotograma original, en píxeles del original.
 * En la versión 1 siempre es `null`: **se guarda la foto sin recortar**, porque
 * guardar solo el recorte es irreversible y §10 exige poder reprocesar con otro
 * pipeline mañana. El campo existe para cuando haya detección previa.
 */
export interface CropBox {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

export interface CaptureRecord {
  readonly schemaVersion: number;
  readonly id: string;
  readonly contributorId: string;
  /**
   * Agrupa varias fotos del **mismo objeto físico**. §10 exige particionar por
   * aportante «y después el objeto físico»: sin esto, cinco ángulos de la misma
   * botella caen unos en train y otros en validación, y la métrica queda
   * inflada.
   */
  readonly objectId: string;
  readonly consentVersion: string;

  readonly material: Material;
  readonly contamination: ContaminationState | null;
  /** Obligatorios: son 🟠 alta en §10 y separan la foto de estudio de la foto de móvil. */
  readonly light: LightCondition;
  readonly angle: Angle;
  /** Opcionales: 🟡 media en §10. Se piden, no se exigen; un campo obligatorio de más cuesta aportes. */
  readonly physicalState: PhysicalState | null;
  readonly background: Background | null;

  readonly mode: CaptureMode;
  /**
   * Qué pidió la misión, si la hubo. Comparado con `material` delata al que
   * confirma sin mirar: si nunca coincide, o coincide siempre demasiado rápido,
   * la etiqueta vale menos.
   */
  readonly requestedMaterial: Material | null;
  /**
   * Matiz opcional en texto libre («vaso de café con tapa»). **No se usa para
   * entrenar**: sirve para descubrir clases que faltan (§10, punto 3).
   */
  readonly note: string | null;
  /**
   * Milisegundos entre ver la foto y confirmar la etiqueta. §10 punto 6: confirmar
   * en menos de ~1 s es señal de no haber mirado. Guardarlo cuesta un entero.
   */
  readonly labelLatencyMs: number;

  readonly quality: QualityMetrics;
  /** pHash perceptual, 64 bits en hexadecimal. Deduplicación contra el pool y contra el control. */
  readonly phash: string;
  readonly image: ImageInfo;
  readonly crop: CropBox | null;
  readonly device: DeviceInfo;

  readonly capturedAt: string;
}

/** Lo que la API devuelve además de lo aportado. */
export interface StoredCapture extends CaptureRecord {
  readonly blobPath: string;
  readonly uploadedAt: string;
  readonly status: ReviewStatus;
  readonly split: DataSplit | null;
  readonly reviewedAt: string | null;
  readonly reviewNote: string | null;
}

interface OptionLabel<T> {
  readonly value: T;
  readonly name: string;
  readonly glyph: string;
}

export const LIGHT_OPTIONS: readonly OptionLabel<LightCondition>[] = [
  { value: "INDOOR", name: "Interior", glyph: "🏠" },
  { value: "OUTDOOR", name: "Exterior", glyph: "☀️" },
  { value: "LOW_LIGHT", name: "Poca luz", glyph: "🌙" },
  { value: "BACKLIT", name: "A contraluz", glyph: "🔆" },
];

export const ANGLE_OPTIONS: readonly OptionLabel<Angle>[] = [
  { value: "TOP_DOWN", name: "Desde arriba", glyph: "⬇️" },
  { value: "OBLIQUE", name: "En diagonal", glyph: "↘️" },
  { value: "SIDE", name: "De lado", glyph: "➡️" },
];

export const PHYSICAL_STATE_OPTIONS: readonly OptionLabel<PhysicalState>[] = [
  { value: "INTACT", name: "Íntegro", glyph: "🟢" },
  { value: "DEFORMED", name: "Aplastado", glyph: "🟡" },
  { value: "BROKEN", name: "Roto", glyph: "🔴" },
];

export const BACKGROUND_OPTIONS: readonly OptionLabel<Background>[] = [
  { value: "BIN", name: "En la caneca", glyph: "🗑️" },
  { value: "GROUND", name: "En el suelo", glyph: "🧱" },
  { value: "TABLE", name: "En una mesa", glyph: "🪵" },
  { value: "BAG", name: "En una bolsa", glyph: "🛍️" },
  { value: "OTHER", name: "Otro", glyph: "❓" },
];

export const MAX_NOTE_LENGTH = 120;

/**
 * Lee lo que el navegador ofrece del dispositivo. Todo opcional: Safari no
 * expone `deviceMemory` y no pasa nada, el campo queda en `null`.
 */
export function readDeviceInfo(): DeviceInfo {
  const nav = navigator as Navigator & { deviceMemory?: number };
  return {
    platform: nav.platform || "desconocida",
    screenWidth: window.screen.width,
    screenHeight: window.screen.height,
    pixelRatio: window.devicePixelRatio,
    memoryGb: typeof nav.deviceMemory === "number" ? nav.deviceMemory : null,
    cores: typeof nav.hardwareConcurrency === "number" ? nav.hardwareConcurrency : null,
  };
}
