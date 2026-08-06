package com.botabien.android.camera

/**
 * Detector de suciedad persistente del lente por diferencia entre frames
 * (RF-016, S12). Determinista y sin ML.
 *
 * Principio: una mancha en el lente está fija en coordenadas de imagen aunque
 * la cámara se mueva; la escena, en cambio, se desliza píxel a píxel. Por eso
 * el detector **solo acumula evidencia mientras detecta movimiento global**:
 * sin movimiento no hay forma de distinguir mancha de escena, y con movimiento
 * la mancha es la única región cuya variación temporal por píxel se anula.
 *
 * La señal es la diferencia temporal por píxel agregada por celda
 * ([LumaGrid.cellTemporalDiff]), no la media de la celda: la media de una
 * región de textura homogénea es invariante al desplazamiento y confundiría
 * el interior de un objeto estático con una mancha; la variación píxel a
 * píxel no — la textura que se desliza cambia cada píxel, la mancha opaca no
 * cambia ninguno.
 *
 * Mecánica por frame:
 * 1. La media de las diferencias temporales de todas las celdas mide el
 *    movimiento global.
 * 2. Con movimiento suficiente, las celdas interiores cuya variación es una
 *    fracción pequeña del movimiento global acumulan persistencia; las demás
 *    la pierden.
 * 3. Una celda que persiste [PERSISTENCE_FRAMES] frames de movimiento dispara
 *    el veredicto, que se retira con histéresis cuando la evidencia decae a
 *    [RETIRE_AT] o menos.
 *
 * El anillo exterior de celdas se excluye: los bordes del encuadre entran y
 * salen de la escena con el movimiento y producirían falsos positivos.
 *
 * Límite documentado: una región perfectamente uniforme y sin textura que
 * llene una celda interior durante todo el horizonte de persistencia es
 * indistinguible de una mancha con esta señal; en uso real el paneo natural
 * entre residuo y canecas rompe esa condición en pocos frames.
 *
 * No es hilo-seguro: se usa desde el hilo del analizador, como el resto del
 * pipeline de calidad.
 */
class PersistentSmudgeDetector : LensSoilingDetector {

    private var previousLuma: ByteArray? = null
    private var previousWidth = 0
    private var previousHeight = 0
    private val cellDiffs = FloatArray(LumaGrid.COLUMNS * LumaGrid.ROWS)
    private val persistence = IntArray(LumaGrid.COLUMNS * LumaGrid.ROWS)
    private var soiling = false

    override fun update(frame: LumaImageFrame): Boolean {
        val previous = previousLuma
        if (previous == null || previousWidth != frame.width || previousHeight != frame.height) {
            // Primer frame o cambio de resolución: fijar la referencia y reempezar.
            previousLuma = frame.luma.copyOf()
            previousWidth = frame.width
            previousHeight = frame.height
            persistence.fill(0)
            return soiling
        }

        LumaGrid.cellTemporalDiff(frame, previous, cellDiffs)
        frame.luma.copyInto(previous)

        val globalMotion = cellDiffs.average().toFloat()
        if (globalMotion >= MOTION_THRESHOLD) {
            accumulateEvidence(globalMotion)

            val strongest = persistence.max()
            if (strongest >= PERSISTENCE_FRAMES) {
                soiling = true
            } else if (strongest <= RETIRE_AT) {
                soiling = false
            }
        }
        // Sin movimiento no hay evidencia nueva en ningún sentido: el veredicto
        // vigente se mantiene hasta poder volver a medir.

        return soiling
    }

    /** Reinicia el estado acumulado, por ejemplo al reiniciar la sesión de cámara. */
    fun reset() {
        persistence.fill(0)
        previousLuma = null
        soiling = false
    }

    private fun accumulateEvidence(globalMotion: Float) {
        val stabilityLimit = maxOf(ABSOLUTE_STABILITY_FLOOR, globalMotion * RELATIVE_STABILITY)
        for (gy in 1 until LumaGrid.ROWS - 1) {
            for (gx in 1 until LumaGrid.COLUMNS - 1) {
                val i = gy * LumaGrid.COLUMNS + gx
                if (cellDiffs[i] < stabilityLimit) {
                    persistence[i] = (persistence[i] + 1).coerceAtMost(PERSISTENCE_CAP)
                } else {
                    persistence[i] = (persistence[i] - DECAY_PER_FRAME).coerceAtLeast(0)
                }
            }
        }
    }

    companion object {
        /**
         * Diferencia temporal media por píxel (niveles de luma) que se
         * considera movimiento global suficiente para poder discriminar.
         */
        const val MOTION_THRESHOLD = 6.0f

        /**
         * Una celda es sospechosa si su variación temporal queda por debajo de
         * esta fracción del movimiento global del frame.
         */
        const val RELATIVE_STABILITY = 0.3f

        /** Suelo absoluto de la variación estable, para escenas de movimiento moderado. */
        const val ABSOLUTE_STABILITY_FLOOR = 2.0f

        /**
         * Frames de movimiento consecutivos que una celda debe permanecer
         * quieta para disparar la sugerencia de limpieza.
         */
        const val PERSISTENCE_FRAMES = 8

        /** Techo del contador: evita que una mancha antigua tarde en retirarse. */
        const val PERSISTENCE_CAP = 12

        /** Velocidad de decaimiento cuando la celda vuelve a variar. */
        const val DECAY_PER_FRAME = 2

        /** Histéresis de retiro: el veredicto cae cuando la evidencia máxima llega aquí. */
        const val RETIRE_AT = 2
    }
}
