/**
 * Contadores por material — lo que alimenta las misiones.
 *
 * Se mantienen materializados en vez de contarse con un `SELECT COUNT` en cada
 * carga de la aplicación: contar documentos en Cosmos cuesta RU proporcionales al
 * tamaño de la colección, y la pantalla de inicio se abre en cada aporte. Un
 * contador por clase cuesta lo mismo con cien fotos que con veinte mil, que es lo
 * que mantiene el gasto dentro de la capa gratuita.
 *
 * Se distinguen dos recuentos y la diferencia importa:
 *
 * - **`collected`** — aportado y con imagen confirmada, esté revisado o no. Es lo
 *   que mueven las misiones: quien aporta tiene que ver avanzar la barra en el
 *   momento, no cuando alguien revise la cola una semana después.
 * - **`approved`** — lo que superó la cuarentena. Es lo único que exporta al pool
 *   de ML (§10, punto 5).
 *
 * Los incrementos usan operaciones `incr` de Cosmos, que son atómicas en el
 * servidor: con varias personas aportando a la vez, leer-modificar-escribir
 * perdería cuentas.
 */

import { PatchOperation } from "@azure/cosmos";
import { isNotFound, statsContainer } from "./cosmos";
import { MATERIALS, type Material } from "./model";

export interface MaterialStats {
  id: Material;
  collected: number;
  collectedContaminated: number;
  approved: number;
  approvedContaminated: number;
  rejected: number;
}

function emptyStats(material: Material): MaterialStats {
  return {
    id: material,
    collected: 0,
    collectedContaminated: 0,
    approved: 0,
    approvedContaminated: 0,
    rejected: 0,
  };
}

async function applyPatch(material: Material, operations: PatchOperation[]): Promise<void> {
  const container = statsContainer();
  try {
    await container.item(material, material).patch(operations);
  } catch (error) {
    if (!isNotFound(error)) throw error;
    // Primera captura de esta clase: se crea el documento y se reintenta. Si dos
    // peticiones simultáneas lo crean a la vez, la segunda recibe un 409 y el
    // reintento del patch encuentra el documento ya creado.
    try {
      await container.items.create(emptyStats(material));
    } catch (conflict) {
      if ((conflict as { code?: number }).code !== 409) throw conflict;
    }
    await container.item(material, material).patch(operations);
  }
}

export function recordCollected(material: Material, contaminated: boolean): Promise<void> {
  const operations: PatchOperation[] = [{ op: "incr", path: "/collected", value: 1 }];
  if (contaminated) operations.push({ op: "incr", path: "/collectedContaminated", value: 1 });
  return applyPatch(material, operations);
}

export function recordApproved(material: Material, contaminated: boolean): Promise<void> {
  const operations: PatchOperation[] = [{ op: "incr", path: "/approved", value: 1 }];
  if (contaminated) operations.push({ op: "incr", path: "/approvedContaminated", value: 1 });
  return applyPatch(material, operations);
}

export function recordRejected(material: Material, contaminated: boolean): Promise<void> {
  // Al rechazar se descuenta de `collected`: si no, la misión daría por cubierta
  // una clase llena de fotos que nunca van a entrar al pool.
  const operations: PatchOperation[] = [
    { op: "incr", path: "/rejected", value: 1 },
    { op: "incr", path: "/collected", value: -1 },
  ];
  if (contaminated) operations.push({ op: "incr", path: "/collectedContaminated", value: -1 });
  return applyPatch(material, operations);
}

export interface TallyEntry {
  total: number;
  contaminated: number;
}

/** Recuento por clase para la aplicación. Devuelve las once, con ceros donde no hay nada. */
export async function readTally(): Promise<Record<Material, TallyEntry>> {
  const { resources } = await statsContainer().items.readAll<MaterialStats>().fetchAll();
  const byMaterial = new Map(resources.map((entry) => [entry.id, entry]));

  const tally = {} as Record<Material, TallyEntry>;
  for (const material of MATERIALS) {
    const stats = byMaterial.get(material);
    tally[material] = {
      total: Math.max(0, stats?.collected ?? 0),
      contaminated: Math.max(0, stats?.collectedContaminated ?? 0),
    };
  }
  return tally;
}
