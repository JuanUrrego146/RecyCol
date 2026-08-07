package com.recycol.data.storage

/**
 * Puerto de almacenamiento clave-valor para preferencias locales (S36, RNF-014).
 *
 * `shared/` no conoce plataformas (RNF-005): cada una aporta su implementación
 * —en Android, DataStore Preferences dentro de `androidApp/`— y las pruebas
 * usan implementaciones deterministas en memoria o sobre archivo.
 */
interface KeyValueStore {

    /** Valor almacenado bajo [key], o `null` si no existe. */
    suspend fun read(key: String): String?

    /** Guarda [value] bajo [key], reemplazando el valor anterior si lo hay. */
    suspend fun write(key: String, value: String)

    /** Elimina el valor almacenado bajo [key], si existe. */
    suspend fun remove(key: String)
}
