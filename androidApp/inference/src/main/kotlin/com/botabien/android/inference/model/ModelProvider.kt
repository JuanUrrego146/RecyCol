package com.botabien.android.inference.model

import android.content.Context
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Puerto interno de carga de modelos empaquetados.
 *
 * Aísla al resto del módulo de dónde viven los archivos `.tflite`. La
 * implementación de producción lee de `assets/`; las pruebas usan dobles.
 * Esta interfaz es estable: cuando el agente ML publique los modelos reales
 * (S27), se dejan caer en `assets/models/` sin tocar ningún consumidor.
 * Opera por nombre de archivo para servir por igual a clasificadores
 * ([ModelSpec]) y detectores ([DetectorModelSpec]).
 */
interface ModelProvider {

    /** Indica si el modelo está empaquetado y se puede cargar. */
    fun isAvailable(assetFileName: String): Boolean

    /**
     * Carga el contenido del modelo listo para el intérprete.
     *
     * @throws IOException si el modelo no existe o no se puede leer.
     */
    fun load(assetFileName: String): ByteBuffer
}

/**
 * [ModelProvider] que lee los modelos de los assets de la app.
 *
 * Intenta primero un mapeo directo a memoria (`mmap`), que es la vía barata:
 * no copia el archivo al heap. Si el asset quedó comprimido en el APK y no se
 * puede mapear, cae a una copia en un búfer directo; más cara pero siempre
 * funciona. Todo es local al dispositivo: sin red (RNF-002).
 */
class AssetModelProvider(
    private val context: Context,
    private val baseDir: String = "models",
) : ModelProvider {

    override fun isAvailable(assetFileName: String): Boolean = try {
        context.assets.open(assetPath(assetFileName)).use { true }
    } catch (_: IOException) {
        false
    }

    override fun load(assetFileName: String): ByteBuffer = try {
        mapFromAssets(assetFileName)
    } catch (_: IOException) {
        copyFromAssets(assetFileName)
    }

    private fun mapFromAssets(assetFileName: String): ByteBuffer =
        context.assets.openFd(assetPath(assetFileName)).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }

    private fun copyFromAssets(assetFileName: String): ByteBuffer =
        context.assets.open(assetPath(assetFileName)).use { stream ->
            val bytes = stream.readBytes()
            ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .put(bytes)
                .apply { rewind() }
        }

    private fun assetPath(assetFileName: String): String = "$baseDir/$assetFileName"
}
