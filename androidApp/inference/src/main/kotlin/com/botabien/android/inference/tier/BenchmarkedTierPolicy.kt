package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature
import com.botabien.domain.port.DeviceTierPolicy
import java.util.ArrayDeque

/**
 * Implementación de producción del puerto [DeviceTierPolicy] (CUS-008,
 * RF-029, RF-030, RF-031).
 *
 * Ciclo de vida:
 * - Se construye leyendo la caché ([TierStore]); sin caché arranca en gama
 *   baja, la postura conservadora (invariante 5), sin bloquear el arranque.
 * - [ensureResolved] (primer arranque) sondea capacidades, corre el
 *   micro-benchmark y cachea el resultado; en arranques siguientes es no-op.
 *   El presupuesto del benchmark garantiza el criterio de los 2 segundos.
 * - [setManualOverride] aplica el ajuste manual del usuario (RF-031): la
 *   gama elegida manda sobre la medida y sobrevive a reinicios; `null`
 *   vuelve al modo automático. Con ajuste manual activo no hay degradación
 *   automática: la decisión explícita del usuario se respeta.
 * - [reportObservedLatencyMillis] recibe la latencia real de clasificación en
 *   uso; en modo automático, si se degrada de forma sostenida (ventana
 *   completa sobre el umbral de la gama), la gama medida baja un escalón y se
 *   re-cachea. Nunca sube en caliente: subir requiere nuevo benchmark en el
 *   siguiente arranque tras [invalidate].
 *
 * La clasificación por cámara no pasa por [isEnabled]: está disponible en las
 * tres gamas y bajo cualquier combinación de ajustes (RNF-001, RF-030).
 */
class BenchmarkedTierPolicy(
    private val store: TierStore,
    private val resolveTier: suspend () -> DeviceTier,
    private val degradationWindow: Int = DEFAULT_WINDOW,
) : DeviceTierPolicy {

    @Volatile
    private var measured: DeviceTier = store.read() ?: DeviceTier.LOW

    @Volatile
    private var manualOverride: DeviceTier? = store.readManualOverride()

    private val recentLatencies = ArrayDeque<Long>(degradationWindow)

    override val tier: DeviceTier
        get() = manualOverride ?: measured

    override fun isEnabled(feature: Feature): Boolean = FeatureMatrix.isEnabled(tier, feature)

    /**
     * Resuelve la gama medida si no hay caché válida. Debe llamarse una vez
     * en el arranque de la app (integración con `androidApp/di/`), fuera del
     * hilo principal. Se resuelve incluso con ajuste manual activo, para que
     * el modo automático tenga valor al volver.
     */
    suspend fun ensureResolved(): DeviceTier {
        store.read()?.let { cached ->
            measured = cached
            return tier
        }
        val resolved = resolveTier()
        measured = resolved
        store.write(resolved)
        return tier
    }

    /**
     * Ajuste manual del nivel de rendimiento (RF-031). Persiste entre
     * reinicios; `null` vuelve al modo automático (gama medida). El punto de
     * entrada del usuario es la pantalla de ajustes (FRONT, S08) a través del
     * caso de uso correspondiente: coordinación pendiente con CORE.
     */
    @Synchronized
    fun setManualOverride(tier: DeviceTier?) {
        manualOverride = tier
        store.writeManualOverride(tier)
        recentLatencies.clear()
    }

    /**
     * Señal de uso real: latencia extremo a extremo de una clasificación.
     * Solo actúa en modo automático; con la ventana completa por encima del
     * umbral de la gama actual, la gama medida baja un escalón de forma
     * permanente (hasta [invalidate]).
     */
    @Synchronized
    fun reportObservedLatencyMillis(millis: Long) {
        if (manualOverride != null) return
        recentLatencies.addLast(millis)
        if (recentLatencies.size > degradationWindow) recentLatencies.removeFirst()
        if (recentLatencies.size < degradationWindow) return

        val threshold = degradeThresholdFor(measured) ?: return
        if (recentLatencies.all { it > threshold }) {
            downgrade()
            recentLatencies.clear()
        }
    }

    /** Borra la gama medida: el siguiente arranque vuelve a medir desde cero. */
    fun invalidate() {
        store.clear()
    }

    private fun downgrade() {
        val lower = when (measured) {
            DeviceTier.HIGH -> DeviceTier.MID
            DeviceTier.MID -> DeviceTier.LOW
            DeviceTier.LOW -> return
        }
        measured = lower
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
