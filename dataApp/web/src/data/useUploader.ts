/**
 * Vaciado de la cola de subida.
 *
 * Reintenta al arrancar, cuando el navegador recupera la red y cuando se encola
 * algo nuevo. Nada de temporizadores agresivos: en un móvil eso es batería
 * gastada en fallar más rápido.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { sendCapture } from "./apiClient";
import { backoffMs, dequeue, isExhausted, listQueued, markFailure } from "./uploadQueue";

export interface UploaderState {
  readonly pending: number;
  readonly stuck: number;
  readonly sending: boolean;
  readonly lastError: string | null;
}

export interface Uploader extends UploaderState {
  /** Vacía la cola. Seguro de llamar en cualquier momento: se ignora si ya está corriendo. */
  readonly flush: () => Promise<void>;
  /** Reintento manual: perdona los intentos agotados y vuelve a empujar. */
  readonly retryAll: () => Promise<void>;
  readonly refresh: () => Promise<void>;
}

export function useUploader(): Uploader {
  const [state, setState] = useState<UploaderState>({
    pending: 0,
    stuck: 0,
    sending: false,
    lastError: null,
  });
  // Guardia de concurrencia en una referencia, no en estado: el estado de React
  // se lee obsoleto dentro del mismo ciclo, así que dos llamadas seguidas —la del
  // montaje y la de guardar un aporte— pasarían las dos y subirían por duplicado.
  // Una referencia se actualiza en el acto.
  const running = useRef(false);
  const [forceRetry, setForceRetry] = useState(0);

  const refresh = useCallback(async () => {
    const queued = await listQueued();
    setState((current) => ({
      ...current,
      pending: queued.length,
      stuck: queued.filter(isExhausted).length,
    }));
  }, []);

  const flush = useCallback(async () => {
    if (running.current) return;
    running.current = true;
    setState((current) => ({ ...current, sending: true }));
    try {
      const queued = await listQueued();
      const ready = queued.filter((item) => forceRetry > 0 || !isExhausted(item));
      let lastError: string | null = null;

      for (const item of ready) {
        try {
          await sendCapture(item.record, item.image);
          await dequeue(item.id);
        } catch (error) {
          const updated = await markFailure(item, error);
          lastError = updated.lastError;
          // Un fallo suele significar que no hay red: parar aquí evita
          // machacar la batería intentando veinte subidas condenadas.
          await wait(backoffMs(updated.attempts));
          break;
        }
      }

      setState((current) => ({ ...current, lastError }));
    } finally {
      running.current = false;
      setState((current) => ({ ...current, sending: false }));
      await refresh();
    }
  }, [forceRetry, refresh]);

  const retryAll = useCallback(async () => {
    setForceRetry((value) => value + 1);
  }, []);

  useEffect(() => {
    void flush();
    // `flush` cambia de identidad en cada render por diseño; encadenarlo aquí
    // provocaría un bucle. Solo interesa el disparo por montaje y por reintento.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [forceRetry]);

  useEffect(() => {
    const onOnline = () => void flush();
    window.addEventListener("online", onOnline);
    return () => window.removeEventListener("online", onOnline);
  }, [flush]);

  return { ...state, flush, retryAll, refresh };
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
