/**
 * `POST /api/captures` es público. Estas pruebas cubren lo que pasa cuando llega
 * algo que no debería: un material inventado, una foto de 40 MB, una fecha de
 * 1970, un cartón sin estado de contaminación.
 *
 * Y cubren el reparto entre entrenamiento y control, que es lo que **no se puede
 * romper**: si un aportante cambia de lado, el control propio deja de ser control
 * y §10 avisa de que ahí es donde se destruye año y medio de evidencia.
 */

import { describe, expect, it } from "vitest";
import {
  CONTROL_SHARE_PERCENT,
  MATERIALS,
  ValidationError,
  assignSplit,
  blobPathFor,
  parseCaptureRecord,
  todayStamp,
} from "./model";

function validBody(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    schemaVersion: 1,
    id: "11111111-2222-3333-4444-555555555555",
    contributorId: "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    objectId: "99999999-8888-7777-6666-555555555555",
    consentVersion: "1.0",
    material: "BEVERAGE_CARTON",
    contamination: "LIQUID",
    light: "INDOOR",
    angle: "OBLIQUE",
    physicalState: "DEFORMED",
    background: "BIN",
    mode: "MISSION",
    requestedMaterial: "BEVERAGE_CARTON",
    note: "vaso de café con tapa",
    labelLatencyMs: 4200,
    quality: { sharpness: 0.42, luminance: 0.5, accepted: true },
    phash: "0f1e2d3c4b5a6978",
    image: { width: 1200, height: 1600, bytes: 320_000, mimeType: "image/jpeg" },
    crop: null,
    device: {
      platform: "Linux armv8l",
      screenWidth: 412,
      screenHeight: 915,
      pixelRatio: 2.6,
      memoryGb: 4,
      cores: 8,
    },
    capturedAt: new Date().toISOString(),
    ...overrides,
  };
}

describe("validación de capturas", () => {
  it("acepta un cuerpo bien formado", () => {
    const record = parseCaptureRecord(validBody());
    expect(record.material).toBe("BEVERAGE_CARTON");
    expect(record.contamination).toBe("LIQUID");
  });

  it("descarta campos que no pertenecen al esquema", () => {
    const record = parseCaptureRecord(validBody({ latitude: 4.71, longitude: -74.07 })) as Record<
      string,
      unknown
    >;
    // Nunca se guarda lo que llegó tal cual: si alguien manda coordenadas, no
    // acaban en la base. §10 descarta la geolocalización explícitamente.
    expect(record.latitude).toBeUndefined();
    expect(record.longitude).toBeUndefined();
  });

  it("rechaza un material fuera de la taxonomía", () => {
    expect(() => parseCaptureRecord(validBody({ material: "PLASTICO" }))).toThrow(ValidationError);
  });

  it("exige el estado de contaminación en fibra", () => {
    for (const material of ["PAPER", "CARDBOARD", "BEVERAGE_CARTON"]) {
      expect(() =>
        parseCaptureRecord(validBody({ material, contamination: null })),
      ).toThrow(/contaminación/);
    }
  });

  it("no lo exige fuera de la fibra", () => {
    const record = parseCaptureRecord(
      validBody({ material: "GLASS", contamination: null, requestedMaterial: "GLASS" }),
    );
    expect(record.contamination).toBeNull();
  });

  it("rechaza un consentimiento retirado", () => {
    expect(() => parseCaptureRecord(validBody({ consentVersion: "0.9" }))).toThrow(
      /consentimiento/i,
    );
  });

  it("rechaza imágenes que no son JPEG", () => {
    expect(() =>
      parseCaptureRecord(
        validBody({ image: { width: 100, height: 100, bytes: 1000, mimeType: "image/png" } }),
      ),
    ).toThrow(/JPEG/);
  });

  it("rechaza imágenes desmesuradas", () => {
    expect(() =>
      parseCaptureRecord(
        validBody({
          image: { width: 1200, height: 1600, bytes: 40_000_000, mimeType: "image/jpeg" },
        }),
      ),
    ).toThrow(ValidationError);
  });

  it("rechaza un fotograma sin imagen aprovechable", () => {
    // Segunda barrera: el cliente ya lo frena, pero este extremo es público.
    const casos = [
      { sharpness: 0.4, luminance: 0, accepted: false }, // cámara tapada
      { sharpness: 0.4, luminance: 1, accepted: false }, // quemado total
      { sharpness: 0, luminance: 0.5, accepted: false }, // un solo tono plano
    ];
    for (const quality of casos) {
      expect(() => parseCaptureRecord(validBody({ quality }))).toThrow(/aprovechable/);
    }
  });

  it("acepta una foto mala pero con contenido", () => {
    // Movida y oscura sigue enseñando; lo que no vale es la que no tiene nada.
    const record = parseCaptureRecord(
      validBody({ quality: { sharpness: 0.05, luminance: 0.1, accepted: false } }),
    );
    expect(record.quality.accepted).toBe(false);
  });

  it("rechaza un pHash mal formado", () => {
    expect(() => parseCaptureRecord(validBody({ phash: "no-es-un-hash" }))).toThrow(/pHash/);
  });

  it("rechaza fechas absurdas", () => {
    expect(() => parseCaptureRecord(validBody({ capturedAt: "1970-01-01T00:00:00Z" }))).toThrow(
      ValidationError,
    );
  });

  it("rechaza recortes: la versión 1 guarda la foto completa", () => {
    expect(() =>
      parseCaptureRecord(validBody({ crop: { x: 0, y: 0, width: 10, height: 10 } })),
    ).toThrow(/recortes/);
  });

  it("recorta una nota demasiado larga rechazándola", () => {
    expect(() => parseCaptureRecord(validBody({ note: "x".repeat(500) }))).toThrow(ValidationError);
  });

  it("tolera un dispositivo que no expone memoria ni núcleos", () => {
    const record = parseCaptureRecord(
      validBody({ device: { platform: "iPhone", screenWidth: 390, screenHeight: 844 } }),
    );
    expect(record.device.memoryGb).toBeNull();
    expect(record.device.cores).toBeNull();
    expect(record.device.pixelRatio).toBe(1);
  });

  it("rechaza un cuerpo que no es un objeto", () => {
    expect(() => parseCaptureRecord("hola")).toThrow(ValidationError);
    expect(() => parseCaptureRecord(null)).toThrow(ValidationError);
  });
});

describe("reparto entre entrenamiento y control", () => {
  it("es estable para el mismo aportante", () => {
    const id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    const first = assignSplit(id);
    for (let i = 0; i < 50; i += 1) expect(assignSplit(id)).toBe(first);
  });

  it("reserva aproximadamente el porcentaje configurado para control", () => {
    const total = 20_000;
    let control = 0;
    for (let i = 0; i < total; i += 1) {
      if (assignSplit(`contribuyente-numero-${i}`) === "CONTROL") control += 1;
    }
    const percent = (control / total) * 100;
    expect(percent).toBeGreaterThan(CONTROL_SHARE_PERCENT - 3);
    expect(percent).toBeLessThan(CONTROL_SHARE_PERCENT + 3);
  });

  it("produce los dos lados", () => {
    const splits = new Set(
      Array.from({ length: 200 }, (_, i) => assignSplit(`persona-${i}`)),
    );
    expect(splits).toEqual(new Set(["TRAIN", "CONTROL"]));
  });
});

describe("rutas de almacenamiento", () => {
  it("agrupa por material y aportante", () => {
    const record = parseCaptureRecord(validBody());
    expect(blobPathFor(record)).toBe(
      "BEVERAGE_CARTON/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/11111111-2222-3333-4444-555555555555.jpg",
    );
  });

  it("cubre las once clases sin colisionar", () => {
    const paths = new Set(
      MATERIALS.map((material) =>
        blobPathFor(
          parseCaptureRecord(
            validBody({
              material,
              contamination: "CLEAN",
              requestedMaterial: null,
              mode: "FREE",
            }),
          ),
        ),
      ),
    );
    expect(paths.size).toBe(MATERIALS.length);
  });
});

describe("marca del día", () => {
  it("usa formato ISO corto", () => {
    expect(todayStamp(new Date("2026-08-07T23:30:00Z"))).toBe("2026-08-07");
  });
});
