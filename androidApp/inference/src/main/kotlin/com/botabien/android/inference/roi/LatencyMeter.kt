package com.botabien.android.inference.roi

/**
 * Medidor liviano de latencia de una operación de inferencia.
 *
 * Registra la última muestra y la media acumulada. Es la instrumentación
 * mínima que pide S16 («medir el coste en latencia del detector»); el banco
 * de medición formal por gama llega con S20/S41 y lee estas mismas cifras.
 * Solo guarda números, jamás frames (RNF-012).
 *
 * @param clock reloj monótono en milisegundos, inyectable en pruebas.
 */
class LatencyMeter(
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {

    /** Duración de la última operación medida, o nulo si aún no hay muestras. */
    @Volatile
    var lastMillis: Long? = null
        private set

    /** Número de muestras acumuladas. */
    @Volatile
    var sampleCount: Long = 0
        private set

    /** Media acumulada de todas las muestras, o nulo si aún no hay. */
    @Volatile
    var averageMillis: Double? = null
        private set

    /** Ejecuta [block] midiendo su duración y registrando la muestra. */
    fun <T> measure(block: () -> T): T {
        val start = clock()
        try {
            return block()
        } finally {
            record(clock() - start)
        }
    }

    @Synchronized
    private fun record(elapsedMillis: Long) {
        lastMillis = elapsedMillis
        sampleCount += 1
        val previous = averageMillis ?: 0.0
        averageMillis = previous + (elapsedMillis - previous) / sampleCount
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
