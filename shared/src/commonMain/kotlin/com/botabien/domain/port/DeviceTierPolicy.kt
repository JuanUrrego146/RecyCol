package com.botabien.domain.port

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature

/**
 * Puerto de la política de gama del dispositivo (CUS-008).
 *
 * Lo implementa el agente EDGE combinando capacidades declaradas (RAM, núcleos,
 * delegados, nivel de API) con un micro-benchmark de latencia real al arrancar.
 * La política se consulta, nunca se asume: toda función costosa pregunta
 * [isEnabled] antes de activarse. Contrato inmutable desde M0.
 */
interface DeviceTierPolicy {

    /** Gama resuelta del dispositivo. */
    val tier: DeviceTier

    /**
     * Indica si una función costosa está habilitada en esta gama.
     * La clasificación por cámara nunca pasa por aquí: funciona en las tres
     * gamas sin excepción (RNF-001).
     */
    fun isEnabled(feature: Feature): Boolean
}
