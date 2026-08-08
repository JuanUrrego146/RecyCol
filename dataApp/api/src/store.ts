/**
 * Almacén de metadatos sobre Azure Table Storage.
 *
 * ## Por qué no Cosmos DB
 *
 * El diseño original usaba Cosmos DB por su capa gratuita permanente. **No se
 * pudo crear**: la suscripción Azure for Students del proyecto rechaza toda
 * creación de cuentas de Cosmos con un `ServiceUnavailable` que dice «alta
 * demanda» pero es un tope de la suscripción — se probaron las cuatro regiones
 * que la política permite, con capa gratuita, sin servidor y aprovisionada.
 * Levantarlo exige una solicitud a Microsoft que tarda días.
 *
 * Table Storage resultó encajar mejor de lo que parecía:
 *
 * - **Su modelo es exactamente el del dominio.** La clave de partición es
 *   `contributorId`, que es literalmente la unidad de partición que exige
 *   `CONTEXTO.md` §10 («la unidad de partición es el usuario»). No hay que
 *   traducir nada.
 * - **Vive en la cuenta de almacenamiento que ya guarda las fotos.** Un recurso
 *   menos, una cadena de conexión menos, una cuota menos que pueda fallar.
 * - **Cuesta menos que Cosmos**: céntimos por millón de operaciones.
 *
 * ## Lo que hubo que resolver
 *
 * 1. **No admite objetos anidados.** El registro completo viaja como JSON en la
 *    propiedad `payload`, y aparte se copian planas solo las columnas por las que
 *    de verdad se filtra (`status`, `material`, `phash`…). Guardarlo entero
 *    aplanado sería frágil y llenaría el límite de 255 propiedades.
 * 2. **No tiene incremento atómico.** Cosmos ofrecía `incr`; aquí hay que leer,
 *    modificar y escribir con `If-Match`, reintentando cuando otro aporte se
 *    adelanta. `updateCounter` encapsula ese bucle: sin él, dos personas
 *    aportando a la vez perderían cuentas.
 * 3. **No tiene índices secundarios.** La cola de moderación sería un barrido de
 *    toda la tabla, así que se mantiene una tabla-índice `pending` con una sola
 *    partición: consultarla es leer en orden de llegada, y revisar borra la
 *    entrada. El coste es una escritura extra por captura.
 */

import {
  odata,
  TableClient,
  TableServiceClient,
  type TableEntity,
} from "@azure/data-tables";
import { config } from "./config";
import type { CaptureDocument, ContributorDocument, Material, ReviewStatus } from "./model";

export const TABLES = {
  captures: "captures",
  contributors: "contributors",
  counters: "counters",
  /** Índice de la cola de moderación. Ver el punto 3 de la cabecera. */
  pending: "pendingreview",
} as const;

const clients = new Map<string, TableClient>();

function tableClient(name: string): TableClient {
  const existing = clients.get(name);
  if (existing) return existing;
  const client = TableClient.fromConnectionString(config.storageConnectionString, name);
  clients.set(name, client);
  return client;
}

/** Crea las tablas si faltan. Idempotente y barato: se llama en el arranque en frío. */
export async function ensureTables(): Promise<void> {
  const service = TableServiceClient.fromConnectionString(config.storageConnectionString);
  await Promise.all(
    Object.values(TABLES).map((name) =>
      service.createTable(name).catch((error: { statusCode?: number }) => {
        if (error.statusCode !== 409) throw error;
      }),
    ),
  );
}

function isNotFound(error: unknown): boolean {
  return typeof error === "object" && error !== null && (error as { statusCode?: number }).statusCode === 404;
}

/**
 * La etiqueta de versión de una entidad, para la escritura condicional.
 *
 * El tipo genérico de `TableEntity` la expone como `unknown` por su firma de
 * índice; en la respuesta real siempre es una cadena. Sin ella, `updateEntity`
 * escribiría incondicionalmente y el control de concurrencia dejaría de existir.
 */
function etagOf(entity: TableEntity): string | undefined {
  return typeof entity.etag === "string" ? entity.etag : undefined;
}

// --- Capturas -----------------------------------------------------------------

interface CaptureEntity extends TableEntity {
  /** Registro completo en JSON. Table Storage no admite objetos anidados. */
  payload: string;
  // Columnas planas: solo las que se filtran u ordenan.
  status: ReviewStatus;
  material: string;
  contributorId: string;
  phash: string;
  imageUploaded: boolean;
  split: string;
  registeredAt: string;
}

function toEntity(capture: CaptureDocument): CaptureEntity {
  return {
    partitionKey: capture.contributorId,
    rowKey: capture.id,
    payload: JSON.stringify(capture),
    status: capture.status,
    material: capture.material,
    contributorId: capture.contributorId,
    phash: capture.phash,
    imageUploaded: capture.imageUploaded,
    split: capture.split,
    registeredAt: capture.registeredAt,
  };
}

function fromEntity(entity: CaptureEntity): CaptureDocument {
  return JSON.parse(entity.payload) as CaptureDocument;
}

export async function readCapture(
  contributorId: string,
  captureId: string,
): Promise<CaptureDocument | null> {
  try {
    const entity = await tableClient(TABLES.captures).getEntity<CaptureEntity>(
      contributorId,
      captureId,
    );
    return fromEntity(entity);
  } catch (error) {
    if (isNotFound(error)) return null;
    throw error;
  }
}

export async function createCapture(capture: CaptureDocument): Promise<void> {
  await tableClient(TABLES.captures).createEntity(toEntity(capture));
}

export async function replaceCapture(capture: CaptureDocument): Promise<void> {
  await tableClient(TABLES.captures).updateEntity(toEntity(capture), "Replace");
}

/** Busca una captura por id sin conocer su partición. Solo para la confirmación de subida. */
export async function findCaptureById(captureId: string): Promise<CaptureDocument | null> {
  const iterator = tableClient(TABLES.captures).listEntities<CaptureEntity>({
    queryOptions: { filter: odata`RowKey eq ${captureId}` },
  });
  for await (const entity of iterator) return fromEntity(entity);
  return null;
}

/** ¿Este aportante ya subió esta misma foto? Consulta de una sola partición. */
export async function hasDuplicate(contributorId: string, phash: string): Promise<boolean> {
  const iterator = tableClient(TABLES.captures).listEntities<CaptureEntity>({
    queryOptions: {
      filter: odata`PartitionKey eq ${contributorId} and phash eq ${phash}`,
    },
  });
  for await (const _entity of iterator) return true;
  return false;
}

export async function listCapturesOf(contributorId: string): Promise<CaptureDocument[]> {
  const iterator = tableClient(TABLES.captures).listEntities<CaptureEntity>({
    queryOptions: { filter: odata`PartitionKey eq ${contributorId}` },
  });
  const captures: CaptureDocument[] = [];
  for await (const entity of iterator) captures.push(fromEntity(entity));
  return captures;
}

export interface Page<T> {
  items: T[];
  continuationToken: string | null;
}

/** Capturas aprobadas, paginadas. Alimenta el manifiesto de ML. */
export async function listApproved(
  pageSize: number,
  continuationToken?: string,
): Promise<Page<CaptureDocument>> {
  const iterator = tableClient(TABLES.captures)
    .listEntities<CaptureEntity>({
      queryOptions: { filter: odata`status eq 'APPROVED' and imageUploaded eq true` },
    })
    .byPage({ maxPageSize: pageSize, continuationToken });

  const page = await iterator.next();
  if (page.done || !page.value) return { items: [], continuationToken: null };
  return {
    items: page.value.map(fromEntity),
    continuationToken: page.value.continuationToken ?? null,
  };
}

/** Todas las aprobadas, en crudo y sin paginar. Solo para el informe académico. */
export async function eachApproved(
  visit: (capture: CaptureDocument) => void,
): Promise<void> {
  const iterator = tableClient(TABLES.captures).listEntities<CaptureEntity>({
    queryOptions: { filter: odata`status eq 'APPROVED' and imageUploaded eq true` },
  });
  for await (const entity of iterator) visit(fromEntity(entity));
}

// --- Índice de la cola de moderación ------------------------------------------

const PENDING_PARTITION = "PENDING";

interface PendingEntity extends TableEntity {
  contributorId: string;
  captureId: string;
}

/**
 * La clave de fila lleva delante la marca de tiempo para que Table Storage —que
 * devuelve por orden de clave— entregue las capturas en el orden en que
 * llegaron, sin ordenar nada después.
 */
function pendingRowKey(capture: CaptureDocument): string {
  return `${capture.registeredAt}_${capture.id}`;
}

export async function addPending(capture: CaptureDocument): Promise<void> {
  await tableClient(TABLES.pending)
    .createEntity<PendingEntity>({
      partitionKey: PENDING_PARTITION,
      rowKey: pendingRowKey(capture),
      contributorId: capture.contributorId,
      captureId: capture.id,
    })
    .catch((error: { statusCode?: number }) => {
      // 409: ya estaba en la cola. Confirmar dos veces la misma captura no debe
      // duplicar la entrada ni romper la petición.
      if (error.statusCode !== 409) throw error;
    });
}

export async function removePending(capture: CaptureDocument): Promise<void> {
  await tableClient(TABLES.pending)
    .deleteEntity(PENDING_PARTITION, pendingRowKey(capture))
    .catch((error: { statusCode?: number }) => {
      if (error.statusCode !== 404) throw error;
    });
}

export async function listPending(
  pageSize: number,
  continuationToken?: string,
): Promise<Page<{ contributorId: string; captureId: string }>> {
  const iterator = tableClient(TABLES.pending)
    .listEntities<PendingEntity>({
      queryOptions: { filter: odata`PartitionKey eq ${PENDING_PARTITION}` },
    })
    .byPage({ maxPageSize: pageSize, continuationToken });

  const page = await iterator.next();
  if (page.done || !page.value) return { items: [], continuationToken: null };
  return {
    items: page.value.map((entity) => ({
      contributorId: entity.contributorId,
      captureId: entity.captureId,
    })),
    continuationToken: page.value.continuationToken ?? null,
  };
}

// --- Aportantes ---------------------------------------------------------------

interface ContributorEntity extends TableEntity {
  payload: string;
  hasAccount: boolean;
}

export async function readContributor(id: string): Promise<ContributorDocument | null> {
  try {
    const entity = await tableClient(TABLES.contributors).getEntity<ContributorEntity>(id, id);
    return JSON.parse(entity.payload) as ContributorDocument;
  } catch (error) {
    if (isNotFound(error)) return null;
    throw error;
  }
}

export async function upsertContributor(contributor: ContributorDocument): Promise<void> {
  await tableClient(TABLES.contributors).upsertEntity<ContributorEntity>(
    {
      partitionKey: contributor.id,
      rowKey: contributor.id,
      payload: JSON.stringify(contributor),
      hasAccount: contributor.account !== null,
    },
    "Replace",
  );
}

/** Aportantes con cuenta. Alimenta el informe académico y las sugerencias. */
export async function listAccountContributors(): Promise<ContributorDocument[]> {
  const iterator = tableClient(TABLES.contributors).listEntities<ContributorEntity>({
    queryOptions: { filter: odata`hasAccount eq true` },
  });
  const contributors: ContributorDocument[] = [];
  for await (const entity of iterator) {
    contributors.push(JSON.parse(entity.payload) as ContributorDocument);
  }
  return contributors;
}

/**
 * Modifica un aportante bajo control de concurrencia optimista.
 *
 * Table Storage no tiene modificación parcial atómica, así que se lee, se
 * transforma y se escribe con `If-Match`. Si otro proceso se adelantó, el
 * servidor responde 412 y se repite con el valor recién leído. Sin esto, dos
 * peticiones simultáneas del mismo aportante —subir una foto y guardar el
 * perfil, por ejemplo— se pisarían la una a la otra.
 */
export async function updateContributor(
  id: string,
  transform: (current: ContributorDocument) => ContributorDocument | null,
  attempts = 5,
): Promise<ContributorDocument | null> {
  const client = tableClient(TABLES.contributors);
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    let entity;
    try {
      entity = await client.getEntity<ContributorEntity>(id, id);
    } catch (error) {
      if (isNotFound(error)) return null;
      throw error;
    }

    const current = JSON.parse(entity.payload) as ContributorDocument;
    const next = transform(current);
    if (!next) return current;

    try {
      await client.updateEntity<ContributorEntity>(
        {
          partitionKey: id,
          rowKey: id,
          payload: JSON.stringify(next),
          hasAccount: next.account !== null,
        },
        "Replace",
        { etag: entity.etag },
      );
      return next;
    } catch (error) {
      if ((error as { statusCode?: number }).statusCode !== 412) throw error;
      // Otro proceso escribió entre la lectura y la escritura: se reintenta.
    }
  }
  throw new Error(`No se pudo actualizar el aportante ${id} tras ${attempts} intentos`);
}

// --- Contadores ---------------------------------------------------------------

const COUNTER_PARTITION = "counter";

interface CounterEntity extends TableEntity {
  collected: number;
  collectedContaminated: number;
  approved: number;
  approvedContaminated: number;
  rejected: number;
}

const EMPTY_COUNTER = {
  collected: 0,
  collectedContaminated: 0,
  approved: 0,
  approvedContaminated: 0,
  rejected: 0,
};

/**
 * Suma sobre un contador con reintento.
 *
 * Es el sustituto de la operación `incr` de Cosmos, que era atómica en el
 * servidor. Aquí la atomicidad la da el `If-Match`: si dos personas aportan a la
 * vez, la segunda escritura falla con 412 y se rehace sobre el valor nuevo. Un
 * simple leer-modificar-escribir perdería cuentas justo cuando más gente use la
 * aplicación.
 */
export async function updateCounter(
  material: Material,
  deltas: Partial<typeof EMPTY_COUNTER>,
  attempts = 6,
): Promise<void> {
  const client = tableClient(TABLES.counters);

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    let current: CounterEntity | null = null;
    try {
      current = await client.getEntity<CounterEntity>(COUNTER_PARTITION, material);
    } catch (error) {
      if (!isNotFound(error)) throw error;
    }

    const base = current ?? { ...EMPTY_COUNTER };
    const next: CounterEntity = {
      partitionKey: COUNTER_PARTITION,
      rowKey: material,
      collected: base.collected + (deltas.collected ?? 0),
      collectedContaminated: base.collectedContaminated + (deltas.collectedContaminated ?? 0),
      approved: base.approved + (deltas.approved ?? 0),
      approvedContaminated: base.approvedContaminated + (deltas.approvedContaminated ?? 0),
      rejected: base.rejected + (deltas.rejected ?? 0),
    };

    try {
      if (current) {
        await client.updateEntity(next, "Replace", { etag: etagOf(current) });
      } else {
        await client.createEntity(next);
      }
      return;
    } catch (error) {
      const status = (error as { statusCode?: number }).statusCode;
      // 412: otro proceso escribió antes. 409: otro proceso creó la fila.
      if (status !== 412 && status !== 409) throw error;
    }
  }
  throw new Error(`No se pudo actualizar el contador de ${material} tras ${attempts} intentos`);
}

export interface CounterRow {
  material: string;
  collected: number;
  collectedContaminated: number;
}

export async function readCounters(): Promise<CounterRow[]> {
  const iterator = tableClient(TABLES.counters).listEntities<CounterEntity>({
    queryOptions: { filter: odata`PartitionKey eq ${COUNTER_PARTITION}` },
  });
  const rows: CounterRow[] = [];
  for await (const entity of iterator) {
    rows.push({
      material: entity.rowKey as string,
      collected: entity.collected,
      collectedContaminated: entity.collectedContaminated,
    });
  }
  return rows;
}

/**
 * Cuántas personas han aportado.
 *
 * Table Storage no sabe contar, así que se lleva a mano en una fila aparte. Es
 * un número decorativo de la pantalla de inicio: si se desviara un poco no pasa
 * nada, y por eso no merece el coste de recorrer la tabla en cada carga.
 */
const CONTRIBUTOR_COUNT_KEY = "contributors";

export async function bumpContributorCount(): Promise<void> {
  const client = tableClient(TABLES.counters);
  for (let attempt = 0; attempt < 6; attempt += 1) {
    let current: (TableEntity & { total: number }) | null = null;
    try {
      current = await client.getEntity<TableEntity & { total: number }>(
        COUNTER_PARTITION,
        CONTRIBUTOR_COUNT_KEY,
      );
    } catch (error) {
      if (!isNotFound(error)) throw error;
    }
    const next = {
      partitionKey: COUNTER_PARTITION,
      rowKey: CONTRIBUTOR_COUNT_KEY,
      total: (current?.total ?? 0) + 1,
    };
    try {
      if (current) await client.updateEntity(next, "Replace", { etag: etagOf(current) });
      else await client.createEntity(next);
      return;
    } catch (error) {
      const status = (error as { statusCode?: number }).statusCode;
      if (status !== 412 && status !== 409) throw error;
    }
  }
  // Se rinde en silencio: es un número informativo, no vale tumbar un aporte.
}

export async function readContributorCount(): Promise<number> {
  try {
    const entity = await tableClient(TABLES.counters).getEntity<TableEntity & { total: number }>(
      COUNTER_PARTITION,
      CONTRIBUTOR_COUNT_KEY,
    );
    return entity.total;
  } catch (error) {
    if (isNotFound(error)) return 0;
    throw error;
  }
}
