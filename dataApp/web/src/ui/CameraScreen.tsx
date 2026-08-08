/**
 * Visor y disparador.
 *
 * Solo cámara en vivo. No hay selector de galería y no es un olvido: una imagen
 * de galería puede traer coordenadas en los metadatos, puede ser de otra persona
 * y puede venir de internet. Rompería a la vez la privacidad y la cadena de
 * derechos limpia, que es la mitad del motivo por el que existe esta plataforma.
 */

import { useEffect, useState } from "react";
import { captureFrame, type CapturedImage } from "../capture/image";
import { cameraStatusMessage, useCamera } from "../capture/useCamera";
import { MATERIAL_INFO } from "../domain/materials";
import type { Material } from "../domain/materials";
import { Header, Notice } from "./components";

export function CameraScreen({
  requested,
  sameObject,
  onCaptured,
  onCancel,
}: {
  /** Qué pidió la misión, si la hubo. Solo se recuerda; nunca decide la etiqueta. */
  requested: Material | null;
  /** `true` si es otra toma del mismo objeto físico. */
  sameObject: boolean;
  onCaptured: (image: CapturedImage) => void;
  onCancel: () => void;
}) {
  const { videoRef, status, start } = useCamera();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void start();
  }, [start]);

  const shoot = async () => {
    if (!videoRef.current || busy) return;
    setBusy(true);
    setError(null);
    try {
      onCaptured(await captureFrame(videoRef.current));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo tomar la foto");
    } finally {
      setBusy(false);
    }
  };

  const statusMessage = cameraStatusMessage(status);
  const info = requested ? MATERIAL_INFO[requested] : null;

  return (
    <div className="screen">
      <Header
        right={
          <button type="button" className="button-ghost" onClick={onCancel}>
            Cancelar
          </button>
        }
      />

      {info && (
        <p className="muted">
          <span aria-hidden="true">{info.glyph} </span>
          {sameObject ? "Otro ángulo del mismo objeto" : `Buscando: ${info.name.toLowerCase()}`}
        </p>
      )}

      <div className="viewfinder">
        <video ref={videoRef} playsInline muted autoPlay />
        {status !== "READY" && (
          <div className="overlay">
            {statusMessage ?? "Abriendo la cámara…"}
          </div>
        )}
      </div>

      <p className="tiny">
        Acércate al objeto y que ocupe buena parte del cuadro. No hace falta que esté limpio ni bien
        puesto: cuanto más real, más sirve.
      </p>

      {error && <Notice tone="danger">{error}</Notice>}

      <div className="actions">
        <button
          type="button"
          className="shutter"
          aria-label="Tomar la foto"
          disabled={status !== "READY" || busy}
          onClick={() => void shoot()}
        />
      </div>
    </div>
  );
}
