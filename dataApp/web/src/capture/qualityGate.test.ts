/**
 * Paridad del filtro de calidad con la app y con el pipeline de ML.
 *
 * Son los **mismos patrones sintéticos** que el autochequeo de
 * `ml/quality/frame_quality_gate.py`, que a su vez replica `SyntheticFrames.kt`
 * de las pruebas JVM de S11. Si esta prueba se pone roja, las tres réplicas del
 * filtro han dejado de decir lo mismo y las métricas que se guardan con cada foto
 * dejan de significar lo que dicen significar.
 */

import { describe, expect, it } from "vitest";
import {
  BLURRY_BELOW,
  OVEREXPOSED_ABOVE,
  UNDEREXPOSED_BELOW,
  accepts,
  isBlank,
  lumaFromRgba,
  metricsOf,
  rejectionReason,
  type LumaPlane,
} from "./qualityGate";

const WIDTH = 320;
const HEIGHT = 240;

function checkerboard(): LumaPlane {
  const data = new Uint8ClampedArray(WIDTH * HEIGHT);
  for (let y = 0; y < HEIGHT; y += 1) {
    for (let x = 0; x < WIDTH; x += 1) {
      data[y * WIDTH + x] = ((x >> 2) + (y >> 2)) % 2 === 0 ? 30 : 220;
    }
  }
  return { data, width: WIDTH, height: HEIGHT };
}

function flat(value: number): LumaPlane {
  return { data: new Uint8ClampedArray(WIDTH * HEIGHT).fill(value), width: WIDTH, height: HEIGHT };
}

/** Caja de 5 tomas separable con relleno de ceros, tres pasadas: el mismo desenfoque del autochequeo. */
function boxBlur(source: LumaPlane, passes = 3): LumaPlane {
  let current = Float64Array.from(source.data);
  const { width, height } = source;

  for (let pass = 0; pass < passes; pass += 1) {
    const horizontal = new Float64Array(current.length);
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        let sum = 0;
        for (let d = -2; d <= 2; d += 1) {
          const sx = x + d;
          if (sx >= 0 && sx < width) sum += current[y * width + sx]!;
        }
        horizontal[y * width + x] = sum / 5;
      }
    }
    const vertical = new Float64Array(current.length);
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        let sum = 0;
        for (let d = -2; d <= 2; d += 1) {
          const sy = y + d;
          if (sy >= 0 && sy < height) sum += horizontal[sy * width + x]!;
        }
        vertical[y * width + x] = sum / 5;
      }
    }
    current = vertical;
  }

  const data = new Uint8ClampedArray(current.length);
  for (let i = 0; i < current.length; i += 1) data[i] = Math.trunc(current[i]!);
  return { data, width, height };
}

describe("filtro de calidad", () => {
  it("acepta un patrón nítido y bien expuesto", () => {
    const metrics = metricsOf(checkerboard());
    expect(metrics.blurry).toBe(false);
    expect(metrics.underexposed).toBe(false);
    expect(metrics.overexposed).toBe(false);
    expect(accepts(metrics)).toBe(true);
    expect(rejectionReason(metrics)).toBeNull();
  });

  it("satura la nitidez en 1 con bordes duros", () => {
    expect(metricsOf(checkerboard()).sharpness).toBe(1);
  });

  it("rechaza el mismo patrón desenfocado", () => {
    const metrics = metricsOf(boxBlur(checkerboard()));
    expect(metrics.sharpness).toBeLessThan(BLURRY_BELOW);
    expect(metrics.blurry).toBe(true);
    expect(accepts(metrics)).toBe(false);
  });

  it("rechaza una toma oscura", () => {
    const metrics = metricsOf(flat(18));
    expect(metrics.luminance).toBeCloseTo(18 / 255, 6);
    expect(metrics.luminance).toBeLessThan(UNDEREXPOSED_BELOW);
    expect(metrics.underexposed).toBe(true);
    expect(rejectionReason(metrics)).toContain("luz");
  });

  it("rechaza una toma quemada", () => {
    const metrics = metricsOf(flat(248));
    expect(metrics.luminance).toBeCloseTo(248 / 255, 6);
    expect(metrics.luminance).toBeGreaterThan(OVEREXPOSED_ABOVE);
    expect(metrics.overexposed).toBe(true);
  });

  it("convierte un gris neutro al mismo nivel de luma", () => {
    const rgba = new Uint8ClampedArray(4 * 4 * 4);
    for (let i = 0; i < rgba.length; i += 4) {
      rgba[i] = 128;
      rgba[i + 1] = 128;
      rgba[i + 2] = 128;
      rgba[i + 3] = 255;
    }
    const luma = lumaFromRgba(rgba, 4, 4);
    expect([...luma.data].every((value) => value === 128)).toBe(true);
  });

  it("no revienta con una imagen más pequeña que la rejilla", () => {
    const tiny: LumaPlane = { data: new Uint8ClampedArray(4).fill(120), width: 2, height: 2 };
    expect(metricsOf(tiny).sharpness).toBe(0);
  });
});

/**
 * Distinto del filtro de producción y con otro fin: aquí no se juzga si la foto
 * es buena, sino si contiene algo. Salió de probar la aplicación en el navegador,
 * donde un fotograma completamente negro llegó a guardarse en la cola.
 */
describe("fotogramas vacíos", () => {
  it("marca el negro absoluto", () => {
    expect(isBlank(metricsOf(flat(0)))).toBe(true);
  });

  it("marca el blanco absoluto", () => {
    expect(isBlank(metricsOf(flat(255)))).toBe(true);
  });

  it("marca un tono plano aunque esté bien expuesto", () => {
    // La cámara tapada con el dedo: luz normal y varianza cero.
    const metrics = metricsOf(flat(128));
    expect(metrics.underexposed).toBe(false);
    expect(metrics.overexposed).toBe(false);
    expect(isBlank(metrics)).toBe(true);
  });

  it("no marca una foto mala pero real", () => {
    // Una toma movida sigue enseñando: §10 quiere justamente las condiciones
    // difíciles. Se avisa, pero se puede enviar.
    const metrics = metricsOf(boxBlur(checkerboard()));
    expect(accepts(metrics)).toBe(false);
    expect(isBlank(metrics)).toBe(false);
  });

  it("no marca una foto buena", () => {
    expect(isBlank(metricsOf(checkerboard()))).toBe(false);
  });
});
