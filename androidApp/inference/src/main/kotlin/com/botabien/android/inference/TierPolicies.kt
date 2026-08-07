package com.botabien.android.inference

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature
import com.botabien.domain.port.DeviceTierPolicy

/**
 * Política de gama provisional hasta que S17 implemente la real con
 * micro-benchmark de arranque: asume gama baja y ninguna función costosa.
 *
 * Es la postura más conservadora posible (invariante 5): la clasificación
 * por cámara funciona igualmente, que es lo único innegociable (RNF-001).
 * La inyección la sustituye por la política real en cuanto exista.
 */
object ConservativeTierPolicy : DeviceTierPolicy {

    override val tier: DeviceTier = DeviceTier.LOW

    override fun isEnabled(feature: Feature): Boolean = false
}
