/**
 * Congelar un fotograma de la cámara y dejarlo listo para subir.
 *
 * Tres decisiones que no son de estilo:
 *
 * 1. **Se guarda la foto sin recortar.** §10 lo pide explícitamente: «guardar
 *    solo el recorte es irreversible». Lo único que se reduce es la resolución,
 *    y hasta un tamaño que sigue estando muy por encima de los 224–260 px que
 *    consume el modelo, para poder reprocesar con otro pipeline mañana.
 * 2. **Reencodificar en canvas elimina los metadatos EXIF**, incluidas
 *    coordenadas si alguna vez las hubiera. §10 dice «no capturar
 *    geolocalización» y esto lo garantiza por construcción, no por confiar en
 *    que nadie las mande.
 * 3. **Solo cámara en vivo, no galería.** Una foto de galería puede traer EXIF
 *    con GPS, puede ser de otra persona y puede venir de internet: rompería a la
 *    vez la privacidad y la cadena de derechos limpia, que es medio motivo por
 *    el que esta plataforma existe.
 */

import { metricsOf, lumaFromRgba, accepts, type FrameMetrics } from "./qualityGate";
import { phashFromGrayscale, PHASH_IMAGE_SIZE } from "./phash";

/**
 * Lado máximo del JPEG guardado. 1600 px deja ~250–450 KB por foto: 10 000 fotos
 * caben en ~4 GB de Blob Storage, que cuesta céntimos al mes.
 */
export const MAX_IMAGE_SIDE = 1600;
export const JPEG_QUALITY = 0.85;

export interface CapturedImage {
  readonly blob: Blob;
  /** URL de objeto para la vista previa. Liberar con `releaseCapturedImage`. */
  readonly previewUrl: string;
  readonly width: number;
  readonly height: number;
  readonly quality: FrameMetrics;
  readonly acceptedByProductionGate: boolean;
  readonly phash: string;
}

export async function captureFrame(video: HTMLVideoElement): Promise<CapturedImage> {
  const sourceWidth = video.videoWidth;
  const sourceHeight = video.videoHeight;
  if (sourceWidth === 0 || sourceHeight === 0) {
    throw new Error("La cámara todavía no entrega imagen");
  }

  const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(sourceWidth, sourceHeight));
  const width = Math.round(sourceWidth * scale);
  const height = Math.round(sourceHeight * scale);

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) throw new Error("El navegador no permite dibujar en canvas");
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = "high";
  context.drawImage(video, 0, 0, width, height);

  const pixels = context.getImageData(0, 0, width, height);
  const quality = metricsOf(lumaFromRgba(pixels.data, width, height));
  const phash = phashFromGrayscale(grayscaleThumbnail(canvas));
  const blob = await toJpeg(canvas);

  return {
    blob,
    previewUrl: URL.createObjectURL(blob),
    width,
    height,
    quality,
    acceptedByProductionGate: accepts(quality),
    phash,
  };
}

export function releaseCapturedImage(image: CapturedImage): void {
  URL.revokeObjectURL(image.previewUrl);
}

function grayscaleThumbnail(source: HTMLCanvasElement): Float64Array {
  const canvas = document.createElement("canvas");
  canvas.width = PHASH_IMAGE_SIZE;
  canvas.height = PHASH_IMAGE_SIZE;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) throw new Error("El navegador no permite dibujar en canvas");
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = "high";
  context.drawImage(source, 0, 0, PHASH_IMAGE_SIZE, PHASH_IMAGE_SIZE);

  const { data } = context.getImageData(0, 0, PHASH_IMAGE_SIZE, PHASH_IMAGE_SIZE);
  const gray = new Float64Array(PHASH_IMAGE_SIZE * PHASH_IMAGE_SIZE);
  for (let i = 0, p = 0; p < gray.length; i += 4, p += 1) {
    // Mismo gris que la conversión "L" de Pillow, que es lo que usa imagehash.
    gray[p] = Math.round(0.299 * data[i]! + 0.587 * data[i + 1]! + 0.114 * data[i + 2]!);
  }
  return gray;
}

function toJpeg(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("No se pudo codificar la foto"))),
      "image/jpeg",
      JPEG_QUALITY,
    );
  });
}
