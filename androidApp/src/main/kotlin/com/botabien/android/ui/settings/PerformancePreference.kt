package com.botabien.android.ui.settings

import com.botabien.domain.model.DeviceTier

/**
 * Preferencia manual del nivel de rendimiento (RF-031 · CUS-008).
 *
 * `null` significa modo automático: rige la gama medida por
 * `DeviceTierPolicy`. Un valor explícito es la decisión del usuario y manda
 * sobre el benchmark.
 *
 * Seam provisional (coordinación #94): la semántica calca
 * `TierStore.readManualOverride/writeManualOverride` del agente EDGE (S18).
 * Cuando S18 llegue a main, la implementación pasa a un adaptador sobre
 * `PrefsTierStore` y la preferencia persiste entre reinicios; mientras tanto
 * la de composición de fakes vive en memoria.
 */
interface PerformancePreference {

    /** Nivel elegido por el usuario, o `null` en modo automático. */
    fun read(): DeviceTier?

    /** Persiste el nivel; `null` vuelve al modo automático. */
    fun write(tier: DeviceTier?)
}

/** Implementación en memoria para la composición sobre fakes. */
class InMemoryPerformancePreference : PerformancePreference {

    private var value: DeviceTier? = null

    override fun read(): DeviceTier? = value

    override fun write(tier: DeviceTier?) {
        value = tier
    }
}
