package com.botabien.data.storage

import java.io.File
import java.util.Properties

/**
 * Implementación de [KeyValueStore] sobre un archivo de propiedades, solo para
 * pruebas JVM: permite verificar que las preferencias sobreviven al cierre
 * creando dos instancias sucesivas sobre el mismo archivo (RNF-014). En la
 * aplicación real el puerto lo implementa DataStore, en `androidApp/`.
 */
class PropertiesKeyValueStore(private val file: File) : KeyValueStore {

    override suspend fun read(key: String): String? = load().getProperty(key)

    override suspend fun write(key: String, value: String) {
        val properties = load()
        properties.setProperty(key, value)
        save(properties)
    }

    override suspend fun remove(key: String) {
        val properties = load()
        properties.remove(key)
        save(properties)
    }

    private fun load(): Properties = Properties().apply {
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

    private fun save(properties: Properties) {
        file.outputStream().use { properties.store(it, null) }
    }
}
