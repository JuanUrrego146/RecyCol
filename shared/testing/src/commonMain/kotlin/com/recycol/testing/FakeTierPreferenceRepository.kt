package com.recycol.testing

import com.recycol.domain.model.DeviceTier
import com.recycol.domain.port.TierPreferenceRepository

/**
 * Fake determinista de `TierPreferenceRepository` (implementación real:
 * agente EDGE adaptando su TierStore, S18). Preferencia en memoria;
 * por defecto `null` = selección automática de gama.
 */
class FakeTierPreferenceRepository(
    initialOverride: DeviceTier? = null,
) : TierPreferenceRepository {

    private var override: DeviceTier? = initialOverride

    override suspend fun manualOverride(): DeviceTier? = override

    override suspend fun setManualOverride(tier: DeviceTier?) {
        override = tier
    }
}
