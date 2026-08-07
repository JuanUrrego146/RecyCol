package com.recycol.testing

import com.recycol.domain.model.DeviceTier
import com.recycol.domain.model.Feature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Documenta el contrato del fake de `DeviceTierPolicy`: gama media por defecto
 * y matriz de funciones por gama alineada con el plan de trabajo.
 */
class FakeDeviceTierPolicyTest {

    @Test
    fun porDefectoSimulaGamaMediaConSuMatrizDeFunciones() {
        val policy = FakeDeviceTierPolicy()

        assertEquals(DeviceTier.MID, policy.tier)
        assertTrue(policy.isEnabled(Feature.CONTINUOUS_CLASSIFICATION))
        assertTrue(policy.isEnabled(Feature.OBJECT_DETECTION))
        assertFalse(policy.isEnabled(Feature.AUTOMATIC_CONTAMINATION_INSPECTION))
    }

    @Test
    fun enGamaBajaNingunaFuncionCostosaEstaHabilitada() {
        val policy = FakeDeviceTierPolicy(tier = DeviceTier.LOW)

        Feature.entries.forEach { feature ->
            assertFalse(policy.isEnabled(feature), "No debería estar habilitada: $feature")
        }
    }

    @Test
    fun enGamaAltaTodasLasFuncionesEstanHabilitadas() {
        val policy = FakeDeviceTierPolicy(tier = DeviceTier.HIGH)

        Feature.entries.forEach { feature ->
            assertTrue(policy.isEnabled(feature), "Debería estar habilitada: $feature")
        }
    }

    @Test
    fun laMatrizPuedeSobrescribirsePorConstructor() {
        val policy = FakeDeviceTierPolicy(
            tier = DeviceTier.LOW,
            enabledFeatures = setOf(Feature.CONTINUOUS_CLASSIFICATION),
        )

        assertTrue(policy.isEnabled(Feature.CONTINUOUS_CLASSIFICATION))
        assertFalse(policy.isEnabled(Feature.OBJECT_DETECTION))
    }
}
