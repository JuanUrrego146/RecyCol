package com.recycol.domain.usecase

import com.recycol.domain.model.CaptureHint
import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.ContaminationResult
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.FrameQuality
import com.recycol.domain.model.WasteMaterial
import com.recycol.testing.FakeBinAvailabilityRepository
import com.recycol.testing.FakeFrameQualityAnalyzer
import com.recycol.testing.FakeProfileRepository
import com.recycol.testing.FakeRuleEngine
import com.recycol.testing.FakeWasteClassifier
import com.recycol.testing.StubImageFrame
import com.recycol.testing.TestProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas del caso de uso central de clasificación (CUS-003 a CUS-006),
 * ejecutadas íntegramente contra los fakes de shared/testing: dominio puro.
 */
class ClassifyWasteUseCaseTest {

    private fun useCase(
        quality: FakeFrameQualityAnalyzer = FakeFrameQualityAnalyzer(),
        classifier: FakeWasteClassifier = FakeWasteClassifier(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
        bins: FakeBinAvailabilityRepository = FakeBinAvailabilityRepository(),
    ) = ClassifyWasteUseCase(
        qualityAnalyzer = quality,
        classifier = classifier,
        ruleEngine = FakeRuleEngine(),
        profiles = profiles,
        binAvailability = bins,
    )

    @Test
    fun calidadInsuficienteEmiteIndicacionesSinDecidir() = runTest {
        val badFrame = FrameQuality(
            sharpness = 0.10f,
            luminance = 0.05f,
            lensSoiling = true,
            objectCentered = false,
        )
        val outcome = useCase(quality = FakeFrameQualityAnalyzer(badFrame)).execute(StubImageFrame())

        assertEquals(
            listOf(
                CaptureHint.CLEAN_LENS,
                CaptureHint.MORE_LIGHT,
                CaptureHint.MOVE_CLOSER,
                CaptureHint.CENTER_OBJECT,
            ),
            outcome.hints,
        )
        assertNull(outcome.disposal)
        assertFalse(outcome.needsUserDecision)
    }

    @Test
    fun confianzaBajoElUmbralNoAdivinaYPideDecisionDelUsuario() = runTest {
        val hesitant = FakeWasteClassifier(
            classification = ClassificationResult(WasteMaterial.PLASTIC, confidence = 0.30f),
        )

        val outcome = useCase(classifier = hesitant).execute(StubImageFrame())

        assertTrue(outcome.needsUserDecision)
        assertNull(outcome.disposal)
    }

    @Test
    fun materialSinInspeccionRecibeDecisionDefinitiva() = runTest {
        val outcome = useCase().execute(StubImageFrame())

        assertEquals(TestProfiles.whiteBin, outcome.disposal?.bin)
        assertTrue(outcome.hints.isEmpty())
        assertFalse(outcome.needsUserDecision)
    }

    @Test
    fun materialConReglaDeInspeccionPideLaVistaInterior() = runTest {
        val carton = FakeWasteClassifier(
            classification = ClassificationResult(WasteMaterial.BEVERAGE_CARTON, confidence = 0.90f),
        )

        val outcome = useCase(classifier = carton).execute(StubImageFrame())

        assertEquals(listOf(CaptureHint.POINT_INSIDE), outcome.hints)
        assertEquals(TestProfiles.whiteBin, outcome.disposal?.bin, "Decisión preliminar con contaminación desconocida")
    }

    @Test
    fun laVistaInteriorContaminadaDegradaLaDecision() = runTest {
        val contaminated = FakeWasteClassifier(
            contamination = ContaminationResult(ContaminationState.CONTAMINATED, confidence = 0.85f),
        )

        val outcome = useCase(classifier = contaminated)
            .resolveContamination(WasteMaterial.BEVERAGE_CARTON, StubImageFrame())

        assertEquals(TestProfiles.blackBin, outcome.disposal?.bin)
        assertTrue(outcome.disposal?.degradedByContamination == true)
    }

    @Test
    fun inspeccionInconcluyentePideDecisionDelUsuario() = runTest {
        val unsure = FakeWasteClassifier(
            contamination = ContaminationResult(ContaminationState.CONTAMINATED, confidence = 0.20f),
        )

        val outcome = useCase(classifier = unsure)
            .resolveContamination(WasteMaterial.BEVERAGE_CARTON, StubImageFrame())

        assertTrue(outcome.needsUserDecision)
        assertNull(outcome.disposal)
    }

    @Test
    fun sinLaCanecaIdealDisponibleSeProponeLaConservadora() = runTest {
        val onlyBlack = FakeBinAvailabilityRepository(initialBins = setOf(TestProfiles.blackBin.id))

        val outcome = useCase(bins = onlyBlack).execute(StubImageFrame())

        assertEquals(TestProfiles.blackBin, outcome.disposal?.bin)
    }

    @Test
    fun sinPerfilActivoElCasoDeUsoFallaExplicitamente() = runTest {
        val firstBoot = FakeProfileRepository(initiallyActive = null)

        assertFailsWith<IllegalStateException> {
            useCase(profiles = firstBoot).execute(StubImageFrame())
        }
    }
}
