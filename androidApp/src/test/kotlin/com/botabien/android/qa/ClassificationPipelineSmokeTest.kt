package com.botabien.android.qa

import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.Feature
import com.botabien.domain.model.WasteMaterial
import com.botabien.testing.FakeDeviceTierPolicy
import com.botabien.testing.FakeFrameQualityAnalyzer
import com.botabien.testing.FakeRuleEngine
import com.botabien.testing.FakeWasteClassifier
import com.botabien.testing.StubImageFrame
import com.botabien.testing.TestProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Prueba de humo del pipeline de clasificación completo sobre los fakes del
 * contrato M0 (CUS-003, CUS-005, CUS-008).
 *
 * Verifica que los puertos encajan entre sí de extremo a extremo: calidad de
 * frame → clasificación de material → motor de reglas → caneca destino. Cuando
 * los agentes CAM, EDGE y RULES sustituyan cada fake por su implementación
 * real, este mismo recorrido es el que debe seguir pasando (S40 lo hará en
 * dispositivo real).
 */
class ClassificationPipelineSmokeTest {

    private val frame = StubImageFrame()
    private val ruleEngine = FakeRuleEngine()
    private val profile = TestProfiles.threeBins

    @Test
    fun elFlujoFelizResuelveLaCanecaDelMaterial() = runTest {
        val quality = FakeFrameQualityAnalyzer().analyze(frame)
        assertTrue(quality.sharpness > 0.5f, "El fake por defecto entrega un frame nítido")

        val result = FakeWasteClassifier().classify(frame)
        assertEquals(WasteMaterial.PLASTIC, result.material)

        val disposal = ruleEngine.resolve(
            material = result.material,
            contamination = ContaminationState.UNKNOWN,
            availableBins = emptySet(),
            profile = profile,
        )

        assertEquals(TestProfiles.whiteBin, disposal.bin)
        assertEquals(DisposalRoute.RECYCLABLE, disposal.route)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun unReciclableContaminadoSeDegradaALaCanecaAlternativa() = runTest {
        val classifier = FakeWasteClassifier(
            classification = ClassificationResult(WasteMaterial.BEVERAGE_CARTON, confidence = 0.88f),
            contamination = ContaminationResult(ContaminationState.CONTAMINATED, confidence = 0.91f),
        )

        val material = classifier.classify(frame).material
        val contamination = classifier.inspectContamination(frame).state

        val disposal = ruleEngine.resolve(
            material = material,
            contamination = contamination,
            availableBins = emptySet(),
            profile = profile,
        )

        assertEquals(TestProfiles.blackBin, disposal.bin, "El vaso contaminado se degrada a la negra")
        assertTrue(disposal.degradedByContamination)
    }

    @Test
    fun sinLaCanecaIdealSeCaeALaConservadora() = runTest {
        val material = FakeWasteClassifier().classify(frame).material

        val disposal = ruleEngine.resolve(
            material = material,
            contamination = ContaminationState.CLEAN,
            availableBins = setOf(TestProfiles.greenBin.id, TestProfiles.blackBin.id),
            profile = profile,
        )

        assertEquals(profile.conservativeBin, disposal.bin.id)
        assertEquals(DisposalRoute.NON_RECYCLABLE, disposal.route)
    }

    @Test
    fun laClasificacionResuelveEnLasTresGamasSinExcepcion() = runTest {
        // Requisito duro del plan: la gama degrada funciones auxiliares, nunca
        // la clasificación. El vocabulario de Feature no contiene ningún
        // interruptor para la clasificación en sí, y la resolución completa
        // funciona igual con la matriz de cada gama.
        DeviceTier.entries.forEach { tier ->
            val policy = FakeDeviceTierPolicy(tier)

            val disposal = ruleEngine.resolve(
                material = FakeWasteClassifier().classify(frame).material,
                contamination = ContaminationState.UNKNOWN,
                availableBins = emptySet(),
                profile = profile,
            )

            assertEquals(TestProfiles.whiteBin, disposal.bin, "La gama $tier clasifica y resuelve caneca")
            if (tier == DeviceTier.LOW) {
                assertFalse(
                    policy.isEnabled(Feature.CONTINUOUS_CLASSIFICATION),
                    "En gama baja la clasificación es bajo demanda, no continua",
                )
            }
        }
    }
}
