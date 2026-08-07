package com.recycol.android.camera

/**
 * Anillo de búferes reutilizables para el plano de luminancia.
 *
 * El analizador de CameraX produce frames de forma continua; asignar un
 * `ByteArray` nuevo por frame generaría presión de GC sostenida y el criterio
 * de hecho de S10 exige memoria estable en sesiones largas. Con un anillo de
 * tamaño fijo, el consumo de memoria es constante desde el primer segundo.
 *
 * Contrato de validez: el búfer devuelto por [nextSlot] se sobreescribirá
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

    private val buffers = arrayOfNulls<ByteArray>(slots)
    private var index = 0

    /**
     * Devuelve el siguiente búfer del anillo con capacidad exacta [size],
     * reutilizando el existente si el tamaño no cambió.
     */
    fun nextSlot(size: Int): ByteArray {
        val current = buffers[index]
        val buffer = if (current != null && current.size == size) {
            current
        } else {
            ByteArray(size).also { buffers[index] = it }
        }
        index = (index + 1) % slots
        return buffer
    }

    companion object {
        const val DEFAULT_SLOTS = 3
    }
}
