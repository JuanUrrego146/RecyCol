package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier

/**
 * Cadencia de análisis del flujo de cámara por gama (RNF-001, matriz de
 * gamas del proyecto): gama baja clasifica bajo demanda con botón; media,
 * continua a ~5 fps; alta, continua a ~10 fps.
 *
 * El módulo de cámara (agente CAM) consulta el intervalo y usa
 * [FrameThrottle] para descartar frames sin costo antes de analizarlos:
 * los frames descartados no se copian, no se preprocesan y no tocan memoria.
 */
object AnalysisCadence {

    /**
     * Intervalo objetivo entre análisis para la gama, o nulo si la gama
     * clasifica bajo demanda (sin análisis continuo).
     */
    fun targetIntervalMillisFor(tier: DeviceTier): Long? = when (tier) {
        DeviceTier.LOW -> null
        DeviceTier.MID -> MID_INTERVAL_MILLIS
        DeviceTier.HIGH -> HIGH_INTERVAL_MILLIS
    }

    /** ~5 fps en gama media. */
    const val MID_INTERVAL_MILLIS = 200L

    /** ~10 fps en gama alta. */
    const val HIGH_INTERVAL_MILLIS = 100L
}

/**
 * Compuerta de cadencia: deja pasar como máximo un frame por intervalo.
 *
 * @param intervalMillis intervalo mínimo entre frames aceptados.
 * @param clock reloj monótono en milisegundos, inyectable en pruebas.
 */
class FrameThrottle(
    private val intervalMillis: Long,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {

    private var lastAcceptedAt: Long? = null

    /** `true` si este frame debe analizarse; `false` si se descarta. */
    @Synchronized
    fun shouldAnalyze(): Boolean {
        val now = clock()
        val last = lastAcceptedAt
        if (last != null && now - last < intervalMillis) return false
        lastAcceptedAt = now
        return true
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
