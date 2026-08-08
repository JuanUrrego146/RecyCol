/**
 * Acceso a Cosmos DB.
 *
 * Por qué Cosmos y no una base relacional: la capa gratuita (1000 RU/s y 25 GB)
 * es permanente, no de doce meses, y la clave de partición natural de este
 * dominio es literalmente `contributorId` — que es la unidad de partición que
 * §10 exige para no inflar las métricas. Lo que la aplicación necesita consultar
 * («las capturas de este aportante», «las pendientes de revisar», «cuántas hay
 * de cada material») encaja en esa partición o en un agregado ya materializado.
 *
 * El cliente se crea una vez por instancia: la función se reutiliza entre
 * invocaciones y abrir una conexión por petición desperdicia latencia y RU.
 */

import { Container, CosmosClient, Database } from "@azure/cosmos";
import { COSMOS_CONTAINERS, config } from "./config";

let client: CosmosClient | null = null;
let database: Database | null = null;

function getDatabase(): Database {
  if (!database) {
    client = client ?? new CosmosClient(config.cosmosConnectionString);
    database = client.database(config.databaseName);
  }
  return database;
}

export function capturesContainer(): Container {
  return getDatabase().container(COSMOS_CONTAINERS.captures);
}

export function contributorsContainer(): Container {
  return getDatabase().container(COSMOS_CONTAINERS.contributors);
}

export function statsContainer(): Container {
  return getDatabase().container(COSMOS_CONTAINERS.stats);
}

/** `true` si el error de Cosmos es un 404. Evita repetir la comprobación de `code` por todos lados. */
export function isNotFound(error: unknown): boolean {
  return typeof error === "object" && error !== null && (error as { code?: number }).code === 404;
}
