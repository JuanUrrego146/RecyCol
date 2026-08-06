package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.port.TierPreferenceRepository

/**
 * Implementación EDGE del puerto [TierPreferenceRepository] (RF-031,
 * coordinación #94): la costura por la que la pantalla de ajustes (FRONT,
 * vía `AdjustPerformanceUseCase`) llega al ajuste manual de gama sin hablar
 * con este módulo directamente (invariante 4).
 *
 * La lectura sale del [TierStore] (la fuente persistida); la escritura pasa
 * por [BenchmarkedTierPolicy.setManualOverride], que además de persistir
 * actualiza la gama efectiva en caliente — y el adaptador de gama (#102)
 * recambia modelo y ROI en la siguiente clasificación.
 */
class PolicyTierPreferenceRepository(
    private val policy: BenchmarkedTierPolicy,
    private val store: TierStore,
) : TierPreferenceRepository {

    override suspend fun manualOverride(): DeviceTier? = store.readManualOverride()

    override suspend fun setManualOverride(tier: DeviceTier?) {
        policy.setManualOverride(tier)
    }
}
