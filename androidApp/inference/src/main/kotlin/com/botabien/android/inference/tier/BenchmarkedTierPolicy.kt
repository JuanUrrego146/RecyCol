package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature
import com.botabien.domain.port.DeviceTierPolicy
import java.util.ArrayDeque

/**
 * Implementación de producción del puerto [DeviceTierPolicy] (CUS-008, RF-029).
 *
 * Ciclo de vida:
 * - Se construye leyendo la caché ([TierStore]); sin caché arranca en gama
 *   baja, la postura conservadora (invariante 5), sin bloquear el arranque.
 * - [ensureResolved] (primer arranque) sondea capacidades, corre el
 *   micro-benchmark y cachea el resultado; en arranques siguientes es no-op.
 *   El presupuesto del benchmark garantiza el criterio de los 2 segundos.
 * - [reportObservedLatencyMillis] recibe la latencia real de clasificación en
 *   uso; si se degrada de forma sostenida (ventana completa sobre el umbral
 *   de la gama), la gama baja un escalón y se re-cachea. Nunca sube en
 *   caliente: subir requiere nuevo benchmark en el siguiente arranque tras
 *   [invalidate].
 */
class BenchmarkedTierPolicy(
    private val store: TierStore,
    private val resolveTier: suspend () -> DeviceTier,
    private val degradationWindow: Int = DEFAULT_WINDOW,
) : DeviceTierPolicy {

    @Volatile
    private var current: DeviceTier = store.read() ?: DeviceTier.LOW

    private val recentLatencies = ArrayDeque<Long>(degradationWindow)

    override val tier: DeviceTier
        get() = current

    override fun isEnabled(feature: Feature): Boolean = FeatureMatrix.isEnabled(current, feature)

    /**
     * Resuelve la gama si no hay caché válida. Debe llamarse una vez en el
     * arranque de la app (integración con `androidApp/di/`), fuera del hilo
     * principal; el resto de la app puede seguir funcionando mientras tanto
     * con la postura conservadora.
     */
    suspend fun ensureResolved(): DeviceTier {
        store.read()?.let { cached ->
            current = cached
            return cached
        }
        val resolved = resolveTier()
        current = resolved
        store.write(resolved)
        return resolved
    }

    /**
     * Señal de uso real: latencia extremo a extremo de una clasificación.
     * Con la ventana completa por encima del umbral de la gama actual, la
     * gama baja un escalón de forma permanente (hasta [invalidate]).
     */
    @Synchronized
    fun reportObservedLatencyMillis(millis: Long) {
        recentLatencies.addLast(millis)
        if (recentLatencies.size > degradationWindow) recentLatencies.removeFirst()
        if (recentLatencies.size < degradationWindow) return

        val threshold = degradeThresholdFor(current) ?: return
        if (recentLatencies.all { it > threshold }) {
            downgrade()
            recentLatencies.clear()
        }
    }

    /** Borra la caché: el siguiente arranque vuelve a medir desde cero. */
    fun invalidate() {
        store.clear()
    }

    private fun downgrade() {
        val lower = when (current) {
            DeviceTier.HIGH -> DeviceTier.MID
            DeviceTier.MID -> DeviceTier.LOW
            DeviceTier.LOW -> return
        }
        current = lower
        store.write(lower)
    }

    /** Umbral de degradación sostenida por gama; LOW ya no baja más. */
    private fun degradeThresholdFor(tier: DeviceTier): Long? = when (tier) {
        DeviceTier.HIGH -> HIGH_DEGRADE_MILLIS
        DeviceTier.MID -> MID_DEGRADE_MILLIS
        DeviceTier.LOW -> null
    }

    private companion object {
        const val DEFAULT_WINDOW = 12

        /*
         * Umbrales alineados con RNF-001 (objetivo ≤2 s en gama media, ≤4 s
         * en baja): si una gama sostiene latencias peores que el objetivo de
         * la gama inferior, está mal clasificada. Calibración fina: S41.
         */
        const val HIGH_DEGRADE_MILLIS = 2_000L
        const val MID_DEGRADE_MILLIS = 4_000L
    }
}
