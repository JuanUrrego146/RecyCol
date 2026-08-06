package com.botabien.domain.port

import com.botabien.domain.model.DeviceTier

/**
 * Puerto de la preferencia manual de rendimiento (RF-031, coordinación #94).
 *
 * La UI de ajustes no puede hablar con `androidApp/inference/` directamente
 * (invariante 4): este puerto es la costura de dominio. Lo implementa el
 * agente EDGE adaptando su `TierStore` (S18); la política efectiva sigue
 * siendo `DeviceTierPolicy`, que combina esta preferencia con la medición real.
 */
interface TierPreferenceRepository {

    /** Gama forzada por el usuario, o `null` si la selección es automática. */
    suspend fun manualOverride(): DeviceTier?

    /**
     * Fija la gama forzada por el usuario y la persiste.
     * @param tier gama elegida, o `null` para volver a la selección automática.
     */
    suspend fun setManualOverride(tier: DeviceTier?)
}
