package com.botabien.testing

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature
import com.botabien.domain.port.DeviceTierPolicy

/**
 * Fake determinista de `DeviceTierPolicy` (implementación real: agente EDGE, S17).
 *
 * Por defecto simula la gama media con su matriz de funciones del plan;
 * cualquier prueba puede fijar otra gama u otra matriz por constructor.
 */
class FakeDeviceTierPolicy(
    override val tier: DeviceTier = DeviceTier.MID,
    private val enabledFeatures: Set<Feature> = defaultFeaturesFor(tier),
) : DeviceTierPolicy {

    override fun isEnabled(feature: Feature): Boolean = feature in enabledFeatures

    companion object {
        /** Matriz de funciones por gama según la política del plan de trabajo. */
        fun defaultFeaturesFor(tier: DeviceTier): Set<Feature> = when (tier) {
            DeviceTier.LOW -> emptySet()
            DeviceTier.MID -> setOf(
                Feature.CONTINUOUS_CLASSIFICATION,
                Feature.OBJECT_DETECTION,
                Feature.CONTINUOUS_BIN_SCAN,
                Feature.FULL_FRAME_QUALITY_ANALYSIS,
            )
            DeviceTier.HIGH -> setOf(
                Feature.CONTINUOUS_CLASSIFICATION,
                Feature.OBJECT_DETECTION,
                Feature.AUTOMATIC_CONTAMINATION_INSPECTION,
                Feature.CONTINUOUS_BIN_SCAN,
                Feature.FULL_FRAME_QUALITY_ANALYSIS,
            )
        }
    }
}
