package com.recycol.domain.usecase

import com.recycol.domain.model.DeviceTier
import com.recycol.testing.FakeTierPreferenceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pruebas del ajuste manual de rendimiento (RF-031, coordinación #94). */
class AdjustPerformanceUseCaseTest {

    @Test
    fun porDefectoLaSeleccionDeGamaEsAutomatica() = runTest {
        val useCase = AdjustPerformanceUseCase(FakeTierPreferenceRepository())

        assertNull(useCase.manualOverride())
    }

    @Test
    fun fijarUnaGamaLaPersisteYSePuedeConsultar() = runTest {
        val useCase = AdjustPerformanceUseCase(FakeTierPreferenceRepository())

        useCase.setManualOverride(DeviceTier.LOW)

        assertEquals(DeviceTier.LOW, useCase.manualOverride())
    }

    @Test
    fun fijarNuloVuelveALaSeleccionAutomatica() = runTest {
        val useCase = AdjustPerformanceUseCase(
            FakeTierPreferenceRepository(initialOverride = DeviceTier.HIGH),
        )

        useCase.setManualOverride(null)

        assertNull(useCase.manualOverride())
    }
}
