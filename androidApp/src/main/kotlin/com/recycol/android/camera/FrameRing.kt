package com.recycol.android.camera

/**
 * Anillo de búferes reutilizables para los dos planos de cada frame:
 * luminancia (calidad) y píxeles ARGB (clasificación).
 *
 * El analizador de CameraX produce frames de forma continua; asignar búferes
 * nuevos por frame generaría presión de GC sostenida y el criterio de hecho de
 * S10 exige memoria estable en sesiones largas. Con un anillo de tamaño fijo,
 * el consumo de memoria es constante desde el primer segundo.
 *
 * Ambos planos salen de la **misma ranura** y avanzan juntos: si tuvieran
 * anillos separados podrían desincronizarse y un frame acabaría mezclando la
 * luminancia de una captura con los píxeles de otra.
 *
 * Contrato de validez: la ranura devuelta por [nextSlot] se sobreescribirá
 * `slots` llamadas después. Con emisión conflada (a lo sumo un frame en vuelo
 * más el que se está escribiendo), tres ranuras bastan para que ningún
 * colector síncrono lea un búfer a medio escribir.
 *
 * No es hilo-seguro: se usa únicamente desde el hilo del analizador.
 */
class FrameRing(private val slots: Int = DEFAULT_SLOTS) {

    init {
        require(slots >= 2) { "El anillo necesita al menos 2 ranuras" }
    }

    /** Los dos planos de un frame, del mismo tamaño en píxeles. */
    class Slot(val luma: ByteArray, val argb: IntArray)

    private val buffers = arrayOfNulls<Slot>(slots)
    private var index = 0

    /**
     * Devuelve la siguiente ranura del anillo con capacidad exacta
     * [pixelCount], reutilizando la existente si el tamaño no cambió.
     */
    fun nextSlot(pixelCount: Int): Slot {
        val current = buffers[index]
        val slot = if (current != null && current.luma.size == pixelCount) {
            current
        } else {
            Slot(ByteArray(pixelCount), IntArray(pixelCount)).also { buffers[index] = it }
        }
        index = (index + 1) % slots
        return slot
    }

    companion object {
        const val DEFAULT_SLOTS = 3
    }
}
