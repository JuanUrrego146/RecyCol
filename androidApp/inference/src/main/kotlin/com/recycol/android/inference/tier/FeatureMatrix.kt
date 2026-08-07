package com.recycol.android.inference.tier

import com.recycol.domain.model.DeviceTier
import com.recycol.domain.model.Feature

/**
 * Matriz de funciones por gama de `context-for-vibe-coding.md` (RF-030).
 *
 * La clasificación por cámara NO está aquí: funciona en las tres gamas sin
 * excepción y jamás se consulta por `isEnabled` (RNF-001). El ajuste manual
 * del nivel de rendimiento (RF-031) llega en S18 y se aplica por encima de
 * esta matriz.
 */
internal object FeatureMatrix {

    fun isEnabled(tier: DeviceTier, feature: Feature): Boolean = when (feature) {
        Feature.CONTINUOUS_CLASSIFICATION -> tier >= DeviceTier.MID
        Feature.OBJECT_DETECTION -> tier >= DeviceTier.MID
        Feature.AUTOMATIC_CONTAMINATION_INSPECTION -> tier >= DeviceTier.HIGH
        Feature.CONTINUOUS_BIN_SCAN -> tier >= DeviceTier.MID
        Feature.FULL_FRAME_QUALITY_ANALYSIS -> tier >= DeviceTier.MID
    }
}
