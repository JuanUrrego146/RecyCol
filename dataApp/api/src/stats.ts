/**
 * Contadores por material — lo que alimenta las misiones.
 *
 * Se mantienen materializados en vez de contarse en cada carga: Table Storage no
 * sabe contar sin recorrer la tabla, y la pantalla de inicio se abre en cada
 * aporte. Una fila por clase cuesta lo mismo con cien fotos que con veinte mil.
 *
 * Se distinguen dos recuentos y la diferencia importa:
 *
 * - **`collected`** — aportado y con imagen confirmada, esté revisado o no. Es lo
 *   que mueven las misiones: quien aporta tiene que ver avanzar la barra en el
 *   momento, no cuando alguien revise la cola una semana después.
 * - **`approved`** — lo que superó la cuarentena. Es lo único que exporta al pool
 *   de ML (`CONTEXTO.md` §10, punto 5).
 *
 * La suma con control de concurrencia vive en `store.updateCounter`.
 */

import { MATERIALS, type Material } from "./model";
import { readCounters, updateCounter } from "./store";

export function recordCollected(material: Material, contaminated: boolean): Promise<void> {
  return updateCounter(material, {
    collected: 1,
    collectedContaminated: contaminated ? 1 : 0,
  });
}

export function recordApproved(material: Material, contaminated: boolean): Promise<void> {
  return updateCounter(material, {
    approved: 1,
    approvedContaminated: contaminated ? 1 : 0,
  });
}

export function recordRejected(material: Material, contaminated: boolean): Promise<void> {
  // Al rechazar se descuenta de `collected`: si no, la misión daría por cubierta
  // una clase llena de fotos que nunca van a entrar al pool.
  return updateCounter(material, {
    rejected: 1,
    collected: -1,
    collectedContaminated: contaminated ? -1 : 0,
  });
}

export interface TallyEntry {
  total: number;
  contaminated: number;
}

/** Recuento por clase para la aplicación. Devuelve las once, con ceros donde no hay nada. */
export async function readTally(): Promise<Record<Material, TallyEntry>> {
  const rows = await readCounters();
  const byMaterial = new Map(rows.map((row) => [row.material, row]));

  const tally = {} as Record<Material, TallyEntry>;
  for (const material of MATERIALS) {
    const row = byMaterial.get(material);
    tally[material] = {
      total: Math.max(0, row?.collected ?? 0),
      contaminated: Math.max(0, row?.collectedContaminated ?? 0),
    };
  }
  return tally;
}
