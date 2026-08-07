package com.recycol.android.camera

/**
 * Reducción de un frame a una rejilla de celdas fijas. Base común de las
 * heurísticas temporales: detección de suciedad (S12) y cambio de escena en la
 * captura dirigida (S14).
 */
object LumaGrid {

    const val COLUMNS = 8
    const val ROWS = 6

    /** Submuestreo dentro de cada celda, coherente con el resto del pipeline. */
    const val SAMPLE_STEP = 2

    /** Calcula las medias por celda de [frame] sobre [into] (tamaño `COLUMNS * ROWS`). */
    fun cellMeans(frame: LumaImageFrame, into: FloatArray) {
        require(into.size == COLUMNS * ROWS) { "Se esperaba un arreglo de ${COLUMNS * ROWS} celdas" }
        val width = frame.width
        val height = frame.height
        val luma = frame.luma
        val cellWidth = width / COLUMNS
        val cellHeight = height / ROWS

        for (gy in 0 until ROWS) {
            for (gx in 0 until COLUMNS) {
                var sum = 0L
                var count = 0
                val startY = gy * cellHeight
                val startX = gx * cellWidth
                var y = startY
                while (y < startY + cellHeight) {
                    val row = y * width
                    var x = startX
                    while (x < startX + cellWidth) {
                        sum += luma[row + x].toInt() and 0xFF
                        count++
                        x += SAMPLE_STEP
                    }
                    y += SAMPLE_STEP
                }
                into[gy * COLUMNS + gx] = if (count == 0) 0f else sum.toFloat() / count
            }
        }
    }

    /**
     * Diferencia temporal por celda: media de la diferencia absoluta píxel a
     * píxel (submuestreada) entre el frame actual y el plano anterior.
     *
     * Es la señal del detector de suciedad: bajo movimiento de cámara, la
     * escena cambia píxel a píxel en todas partes; una mancha opaca del lente
     * anula esa variación en su región aunque la media de la celda no cambie.
     */
    fun cellTemporalDiff(frame: LumaImageFrame, previousLuma: ByteArray, into: FloatArray) {
        require(into.size == COLUMNS * ROWS) { "Se esperaba un arreglo de ${COLUMNS * ROWS} celdas" }
        val width = frame.width
        val height = frame.height
        val luma = frame.luma
        val cellWidth = width / COLUMNS
        val cellHeight = height / ROWS

        for (gy in 0 until ROWS) {
            for (gx in 0 until COLUMNS) {
                var sum = 0L
                var count = 0
                val startY = gy * cellHeight
                val startX = gx * cellWidth
                var y = startY
                while (y < startY + cellHeight) {
                    val row = y * width
                    var x = startX
                    while (x < startX + cellWidth) {
                        val current = luma[row + x].toInt() and 0xFF
                        val previous = previousLuma[row + x].toInt() and 0xFF
                        sum += if (current >= previous) current - previous else previous - current
                        count++
                        x += SAMPLE_STEP
                    }
                    y += SAMPLE_STEP
                }
                into[gy * COLUMNS + gx] = if (count == 0) 0f else sum.toFloat() / count
            }
        }
    }

    /** Diferencia media absoluta entre dos rejillas, en niveles de luma. */
    fun meanAbsoluteDifference(a: FloatArray, b: FloatArray): Float {
        var total = 0f
        for (i in a.indices) {
            total += kotlin.math.abs(a[i] - b[i])
        }
        return total / a.size
    }
}
