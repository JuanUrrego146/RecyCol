/**
 * Cola de subida persistente en IndexedDB.
 *
 * Por qué existe: se fotografía basura junto a una caneca, en la calle, en un
 * sótano, en un patio. La señal es mala justo donde están los residuos reales,
 * que es exactamente el dominio que este proyecto necesita capturar. Sin cola,
 * cada foto perdida por un fallo de red es una foto que nadie va a repetir, y el
 * aportante concluye que la aplicación no funciona.
 *
 * Con cola, etiquetar y subir se desacoplan: la foto se guarda en el dispositivo
 * en cuanto se etiqueta, y sube sola cuando hay red. IndexedDB guarda `Blob`
 * nativamente, así que no hay que convertir la imagen a base64 —que la inflaría
 * un tercio— para poder encolarla.
 *
 * El envío es idempotente por `captureId`: si la red se cae entre el registro y
 * la subida, el reintento repite los tres pasos sin duplicar el registro.
 */

import type { CaptureRecord } from "../domain/capture";

const DB_NAME = "recycol-aporta";
const DB_VERSION = 1;
const STORE = "queue";

export interface QueuedCapture {
  readonly id: string;
  readonly record: CaptureRecord;
  readonly image: Blob;
  readonly attempts: number;
  readonly lastError: string | null;
  readonly queuedAt: string;
}

let dbPromise: Promise<IDBDatabase> | null = null;

function openDatabase(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: "id" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("No se pudo abrir la base local"));
  });
  return dbPromise;
}

function runTransaction<T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  return openDatabase().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const transaction = db.transaction(STORE, mode);
        const request = operation(transaction.objectStore(STORE));
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error ?? new Error("Fallo en la base local"));
      }),
  );
}

export async function enqueue(record: CaptureRecord, image: Blob): Promise<QueuedCapture> {
  const item: QueuedCapture = {
    id: record.id,
    record,
    image,
    attempts: 0,
    lastError: null,
    queuedAt: new Date().toISOString(),
  };
  await runTransaction("readwrite", (store) => store.put(item));
  return item;
}

export function listQueued(): Promise<QueuedCapture[]> {
  return runTransaction<QueuedCapture[]>("readonly", (store) => store.getAll());
}

export function countQueued(): Promise<number> {
  return runTransaction<number>("readonly", (store) => store.count());
}

export async function dequeue(id: string): Promise<void> {
  await runTransaction("readwrite", (store) => store.delete(id));
}

export async function markFailure(item: QueuedCapture, error: unknown): Promise<QueuedCapture> {
  const updated: QueuedCapture = {
    ...item,
    attempts: item.attempts + 1,
    lastError: error instanceof Error ? error.message : String(error),
  };
  await runTransaction("readwrite", (store) => store.put(updated));
  return updated;
}

/**
 * Tope de reintentos antes de dejar de insistir automáticamente. El aporte no se
 * borra: se queda visible en «pendientes» para que la persona decida reintentar
 * o descartarlo. Borrar en silencio el trabajo de alguien es peor que fallar.
 */
export const MAX_ATTEMPTS = 6;

export function isExhausted(item: QueuedCapture): boolean {
  return item.attempts >= MAX_ATTEMPTS;
}

/** Espera exponencial con tope, para no castigar una red intermitente. */
export function backoffMs(attempts: number): number {
  return Math.min(30_000, 1_000 * 2 ** attempts);
}
