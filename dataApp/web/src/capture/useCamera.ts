/**
 * Acceso a la cámara trasera del móvil desde el navegador.
 *
 * Se pide `facingMode: environment` como preferencia, no como exigencia: en un
 * portátil sin cámara trasera una restricción dura falla con
 * `OverconstrainedError` y el aportante ve un error en vez de su webcam. Se
 * prefiere que funcione en todas partes.
 *
 * La resolución se pide alta (`ideal: 1920`) y luego `image.ts` la reduce. Pedir
 * poco aquí no se puede deshacer después.
 */

import { useCallback, useEffect, useRef, useState } from "react";

export type CameraStatus =
  | "IDLE"
  | "STARTING"
  | "READY"
  /** El usuario dijo que no, o el navegador lo bloqueó. */
  | "DENIED"
  /** No hay cámara, o el navegador no expone `getUserMedia`. */
  | "UNAVAILABLE"
  /** Sin HTTPS no hay cámara. Pasa al abrir la página por IP en la red local. */
  | "INSECURE";

const CONSTRAINTS: MediaStreamConstraints = {
  video: {
    facingMode: { ideal: "environment" },
    width: { ideal: 1920 },
    height: { ideal: 1080 },
  },
  audio: false,
};

export interface CameraController {
  readonly videoRef: React.RefObject<HTMLVideoElement>;
  readonly status: CameraStatus;
  readonly start: () => Promise<void>;
  readonly stop: () => void;
}

export function useCamera(): CameraController {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [status, setStatus] = useState<CameraStatus>("IDLE");

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
    setStatus("IDLE");
  }, []);

  const start = useCallback(async () => {
    if (streamRef.current) return;

    if (!window.isSecureContext) {
      setStatus("INSECURE");
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setStatus("UNAVAILABLE");
      return;
    }

    setStatus("STARTING");
    try {
      const stream = await navigator.mediaDevices.getUserMedia(CONSTRAINTS);
      streamRef.current = stream;
      const video = videoRef.current;
      if (!video) {
        stream.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
        setStatus("IDLE");
        return;
      }
      video.srcObject = stream;
      await video.play();
      setStatus("READY");
    } catch (error) {
      const name = error instanceof DOMException ? error.name : "";
      setStatus(name === "NotAllowedError" || name === "SecurityError" ? "DENIED" : "UNAVAILABLE");
    }
  }, []);

  // Soltar la cámara al desmontar: si no, el piloto del móvil sigue encendido.
  useEffect(() => stop, [stop]);

  return { videoRef, status, start, stop };
}

export function cameraStatusMessage(status: CameraStatus): string | null {
  switch (status) {
    case "DENIED":
      return "Necesitamos la cámara para poder aportar. Actívala en los permisos del navegador y vuelve a intentarlo.";
    case "UNAVAILABLE":
      return "No encontramos una cámara disponible en este dispositivo.";
    case "INSECURE":
      return "El navegador solo da acceso a la cámara en páginas seguras (https).";
    case "IDLE":
    case "STARTING":
    case "READY":
      return null;
  }
}
