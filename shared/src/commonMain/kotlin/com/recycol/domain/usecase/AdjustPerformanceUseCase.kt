package com.recycol.domain.usecase

import com.recycol.domain.model.DeviceTier
import com.recycol.domain.port.TierPreferenceRepository

/**
 * Caso de uso del ajuste manual del nivel de rendimiento (RF-031 · CUS-008,
 * coordinación #94). La pantalla de ajustes lo consume; la política efectiva
 * la sigue calculando `DeviceTierPolicy` (agente EDGE) combinando esta
 * preferencia con la medición real del dispositivo.
 */
class AdjustPerformanceUseCase(
    private val preferences: TierPreferenceRepository,
) {

    /** Gama forzada por el usuario, o `null` si la selección es automática. */
    suspend fun manualOverride(): DeviceTier? = preferences.manualOverride()

    /** Fuerza una gama, o vuelve a la automática con `null`. Persiste entre reinicios. */
    suspend fun setManualOverride(tier: DeviceTier?) {
        preferences.setManualOverride(tier)
    }
}
