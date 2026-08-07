package com.botabien.data.storage

/**
 * Implementación en memoria de [KeyValueStore] para pruebas del módulo de
 * datos. Determinista y sin plataforma; la persistencia real entre sesiones
 * se prueba en jvmTest con una implementación sobre archivo.
 */
class InMemoryKeyValueStore(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : KeyValueStore {

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
