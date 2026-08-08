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
  /**
   * Una sola cadena de conexión para todo: las fotos van a Blob Storage y los
   * metadatos a Table Storage, ambos en la misma cuenta. Ver la cabecera de
   * `store.ts` para por qué no hay Cosmos DB.
   */
  get storageConnectionString(): string {
    return required("STORAGE_CONNECTION_STRING");
  },
  get containerName(): string {
    return optional("STORAGE_CONTAINER", "captures");
  },
  /** Rol de Static Web Apps que da acceso a moderación y exportación. */
  get adminRole(): string {
    return optional("ADMIN_ROLE", "administrador");
  },
};

/** Minutos de vida de la SAS de escritura. Corta a propósito: solo tiene que durar una subida. */
export const UPLOAD_SAS_MINUTES = 15;
/** Minutos de vida de la SAS de lectura que usa la pantalla de moderación. */
export const REVIEW_SAS_MINUTES = 30;
