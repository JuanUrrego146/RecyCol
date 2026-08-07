package com.botabien.android.inference.engine

import java.nio.ByteBuffer

/**
 * Vía de aceleración con la que corre un motor de inferencia.
 * El orden de preferencia por defecto es NNAPI → GPU → CPU; la CPU
 * siempre está disponible y es el respaldo final (RF-014).
 */
enum class AccelerationMode {
    NNAPI,
    GPU,
    CPU,
}

/**
 * Motor de inferencia sobre un modelo ya cargado.
 *
 * Abstrae el intérprete concreto para que la lógica de respaldo y el
 * clasificador sean comprobables sin el runtime nativo de LiteRT.
 * Las implementaciones no son seguras entre hilos: el llamador serializa
 * (lo hace [ResilientInferenceEngine]).
 */
interface InferenceEngine : AutoCloseable {

    /** Vía de aceleración efectiva de este motor. */
    val accelerationMode: AccelerationMode

    /**
     * Ejecuta una inferencia y devuelve las puntuaciones por clase,
     * ya decuantizadas si el tensor de salida es entero.
     *
     * @param input búfer de entrada con la posición en cero, con el layout
     *   que declara la [com.botabien.android.inference.model.ModelSpec] del modelo.
     */
    fun run(input: ByteBuffer): FloatArray

    /**
     * Ejecuta una inferencia sobre un modelo con varios tensores de salida
     * (detectores, RF-010) y los devuelve aplanados, en orden de índice y ya
     * decuantizados. Los modelos de una sola salida devuelven una lista de un
     * elemento.
     */
    fun runMultiOutput(input: ByteBuffer): List<FloatArray> = listOf(run(input))
}

/**
 * Error del runtime de inferencia una vez agotados todos los respaldos.
 * Quien lo recibe (caso de uso / ViewModel) decide la respuesta conservadora;
 * este módulo nunca inventa un resultado.
 */
class InferenceException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
