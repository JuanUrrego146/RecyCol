/**
 * Configuración desde variables de entorno.
 *
 * **Ningún secreto vive en el repositorio.** Las cadenas de conexión se cargan
 * en la configuración de la aplicación de Static Web Apps con
 * `az staticwebapp appsettings set` (ver `dataApp/docs/DESPLIEGUE.md`), que es
 * también por lo que aquí no hay valores por defecto para ellas: si falta una,
 * la función debe fallar de forma ruidosa y no arrancar a medias contra un
 * recurso equivocado.
 */

function required(name: string): string {
  const value = process.env[name];
  if (!value || value.trim().length === 0) {
    throw new Error(
      `Falta la variable de entorno ${name}. Configúrala con "az staticwebapp appsettings set".`,
    );
  }
  return value;
}

function optional(name: string, fallback: string): string {
  const value = process.env[name];
  return value && value.trim().length > 0 ? value : fallback;
}

export const config = {
  get cosmosConnectionString(): string {
    return required("COSMOS_CONNECTION_STRING");
  },
  get storageConnectionString(): string {
    return required("STORAGE_CONNECTION_STRING");
  },
  get databaseName(): string {
    return optional("COSMOS_DATABASE", "recycol");
  },
  get containerName(): string {
    return optional("STORAGE_CONTAINER", "captures");
  },
  /** Rol de Static Web Apps que da acceso a moderación y exportación. */
  get adminRole(): string {
    return optional("ADMIN_ROLE", "administrador");
  },
};

export const COSMOS_CONTAINERS = {
  captures: "captures",
  contributors: "contributors",
  stats: "stats",
} as const;

/** Minutos de vida de la SAS de escritura. Corta a propósito: solo tiene que durar una subida. */
export const UPLOAD_SAS_MINUTES = 15;
/** Minutos de vida de la SAS de lectura que usa la pantalla de moderación. */
export const REVIEW_SAS_MINUTES = 30;
