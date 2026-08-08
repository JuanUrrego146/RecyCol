/**
 * Almacenamiento de imágenes y firmas de acceso.
 *
 * La imagen **no pasa por la función**: el cliente recibe una SAS de escritura y
 * sube el JPEG directamente a Blob Storage. Tres motivos, en orden de peso:
 *
 * 1. Las funciones gestionadas de Static Web Apps tienen un límite de tamaño de
 *    petición que una foto de móvil puede rozar.
 * 2. Es más rápido para quien aporta: un salto menos, y el almacenamiento está
 *    en la misma región.
 * 3. Es más barato: no se paga cómputo por mover bytes.
 *
 * Las firmas son **de un solo blob, de escritura y de vida corta**. Nada de una
 * SAS de contenedor en el cliente: eso sería entregarle a cualquiera la llave
 * para leer y sobrescribir todo el dataset.
 */

import {
  BlobSASPermissions,
  BlobServiceClient,
  ContainerSASPermissions,
  StorageSharedKeyCredential,
  generateBlobSASQueryParameters,
} from "@azure/storage-blob";
import { REVIEW_SAS_MINUTES, UPLOAD_SAS_MINUTES, config } from "./config";

let service: BlobServiceClient | null = null;
let credential: StorageSharedKeyCredential | null = null;

function getService(): BlobServiceClient {
  if (!service) {
    service = BlobServiceClient.fromConnectionString(config.storageConnectionString);
  }
  return service;
}

/**
 * La credencial de clave compartida se saca de la cadena de conexión porque
 * firmar una SAS de usuario exige la clave de la cuenta. Las funciones
 * gestionadas de Static Web Apps no admiten identidad administrada, así que esta
 * es la vía disponible en el plan gratuito.
 */
function getCredential(): StorageSharedKeyCredential {
  if (!credential) {
    const parsed = parseConnectionString(config.storageConnectionString);
    credential = new StorageSharedKeyCredential(parsed.accountName, parsed.accountKey);
  }
  return credential;
}

function parseConnectionString(connectionString: string): {
  accountName: string;
  accountKey: string;
} {
  const parts = new Map<string, string>();
  for (const segment of connectionString.split(";")) {
    const index = segment.indexOf("=");
    if (index > 0) parts.set(segment.slice(0, index), segment.slice(index + 1));
  }
  const accountName = parts.get("AccountName");
  const accountKey = parts.get("AccountKey");
  if (!accountName || !accountKey) {
    throw new Error("STORAGE_CONNECTION_STRING no contiene AccountName y AccountKey");
  }
  return { accountName, accountKey };
}

/** Crea el contenedor si no existe. Privado: los blobs solo se leen con SAS. */
export async function ensureContainer(): Promise<void> {
  await getService().getContainerClient(config.containerName).createIfNotExists();
}

/** SAS de escritura para un blob concreto. Sin permiso de lectura ni de borrado. */
export function uploadSasUrl(blobPath: string): string {
  const blob = getService().getContainerClient(config.containerName).getBlockBlobClient(blobPath);
  const now = Date.now();
  const query = generateBlobSASQueryParameters(
    {
      containerName: config.containerName,
      blobName: blobPath,
      permissions: BlobSASPermissions.parse("cw"),
      // Un minuto de margen hacia atrás: los relojes de los móviles se desvían y
      // una SAS que "empieza en el futuro" produce un 403 desconcertante.
      startsOn: new Date(now - 60_000),
      expiresOn: new Date(now + UPLOAD_SAS_MINUTES * 60_000),
      contentType: "image/jpeg",
    },
    getCredential(),
  ).toString();
  return `${blob.url}?${query}`;
}

/** SAS de lectura para la pantalla de moderación. */
export function readSasUrl(blobPath: string): string {
  const blob = getService().getContainerClient(config.containerName).getBlockBlobClient(blobPath);
  const query = generateBlobSASQueryParameters(
    {
      containerName: config.containerName,
      blobName: blobPath,
      permissions: BlobSASPermissions.parse("r"),
      startsOn: new Date(Date.now() - 60_000),
      expiresOn: new Date(Date.now() + REVIEW_SAS_MINUTES * 60_000),
    },
    getCredential(),
  ).toString();
  return `${blob.url}?${query}`;
}

/**
 * SAS de lectura y listado sobre todo el contenedor, para que el pipeline de ML
 * sincronice el dataset con `azcopy`. **Solo administración**, y de vida corta.
 */
export function containerReadSasUrl(minutes: number): string {
  const container = getService().getContainerClient(config.containerName);
  const query = generateBlobSASQueryParameters(
    {
      containerName: config.containerName,
      permissions: ContainerSASPermissions.parse("rl"),
      startsOn: new Date(Date.now() - 60_000),
      expiresOn: new Date(Date.now() + minutes * 60_000),
    },
    getCredential(),
  ).toString();
  return `${container.url}?${query}`;
}

/** `true` si el blob existe. Lo usa la confirmación para no dar por buena una subida que no llegó. */
export async function blobExists(blobPath: string): Promise<boolean> {
  return getService()
    .getContainerClient(config.containerName)
    .getBlockBlobClient(blobPath)
    .exists();
}
