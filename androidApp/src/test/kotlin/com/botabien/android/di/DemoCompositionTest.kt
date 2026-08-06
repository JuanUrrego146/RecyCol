package com.botabien.android.di

import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.WasteMaterial
import com.botabien.testing.StubImageFrame
import com.botabien.testing.TestProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Humo de la Demo A (#119): el flujo completo del dominio con el motor de
 * reglas REAL (DefaultRuleEngine) y percepción simulada. Es la primera prueba
 * que recorre calidad → clasificación → reglas → decisión de extremo a extremo.
 */
class DemoCompositionTest {

    @Test
    fun elFlujoDeDemoClasificaPlasticoALaCanecaBlancaConElMotorReal() = runTest {
        val useCases = DemoComposition.appUseCases()

        useCases.selectCountry.select(TestProfiles.threeBins.isoCode)
        val outcome = useCases.classifyWaste.execute(StubImageFrame())

        assertEquals(TestProfiles.whiteBin.id, outcome.disposal?.bin?.id)
        assertEquals(DisposalRoute.RECYCLABLE, outcome.disposal?.route)
        assertTrue(outcome.hints.isEmpty())
    }

    @Test
    fun laSeleccionManualDeElectronicoVaAlPuntoEspecialConElMotorReal() = runTest {
        val useCases = DemoComposition.appUseCases()

        useCases.selectCountry.select(TestProfiles.threeBins.isoCode)
        val outcome = useCases.resolveManual.resolve(
            material = WasteMaterial.ELECTRONIC,
            contamination = ContaminationState.UNKNOWN,
        )

        assertEquals(DisposalRoute.SPECIAL_COLLECTION, outcome.disposal?.route)
        assertTrue(outcome.manualSelection)
    }

    @Test
    fun sinSeleccionarPaisElFlujoExigeElOnboarding() = runTest {
        val useCases = DemoComposition.appUseCases()

        val result = runCatching { useCases.classifyWaste.execute(StubImageFrame()) }

        assertTrue(result.isFailure, "El primer arranque exige elegir país (RF-001)")
    }
}
