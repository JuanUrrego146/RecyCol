package com.botabien.android.camera

import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.InspectionRule
import com.botabien.domain.model.WasteMaterial
import com.botabien.testing.FakeWasteClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Criterio de hecho de S14: ante un material con regla de inspección se
 * solicita la vista interior, se captura y se entrega; si el usuario no la
 * da, se aplica la ruta conservadora.
 */
class DirectedCaptureControllerTest {

    private val rule = InspectionRule(
        material = WasteMaterial.PAPER,
        promptKey = "inspection.point_inside",
        requiresInteriorView = true,
    )

    private fun controller() = DirectedCaptureController(HeuristicFrameQualityAnalyzer())

    /** Vista exterior: la escena que ve la cámara al pedirse la inspección. */
    private fun exterior() = SyntheticFrames.frame(SyntheticFrames.blockNoise(seed = 3))

    /** Vista interior nítida y bien expuesta: escena claramente distinta. */
    private fun interior() = SyntheticFrames.frame(SyntheticFrames.blockNoise(seed = 40))

    @Test
    fun `begin publica la solicitud con la clave del perfil`() {
        val controller = controller()

        controller.begin(rule, nowMillis = 0)

        val state = assertIs<DirectedCaptureController.State.Requesting>(controller.state)
        assertEquals("inspection.point_inside", state.promptKey)
    }

    @Test
    fun `la vista exterior sin reorientar no se captura aunque sea nitida`() {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)

        controller.onFrame(exterior(), nowMillis = 0)
        val state = controller.onFrame(exterior(), nowMillis = 2_000)

        assertIs<DirectedCaptureController.State.Requesting>(
            state,
            "Sin cambio de escena se estaría capturando la vista equivocada",
        )
    }

    @Test
    fun `reorientar a una vista nitida captura el frame dirigido`() {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)

        controller.onFrame(exterior(), nowMillis = 0)
        val state = controller.onFrame(interior(), nowMillis = 1_500)

        assertIs<DirectedCaptureController.State.Captured>(state)
    }

    @Test
    fun `una vista interior borrosa no se captura hasta enfocarse`() {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)
        controller.onFrame(exterior(), nowMillis = 0)

        val blurry = SyntheticFrames.frame(
            SyntheticFrames.boxBlur(SyntheticFrames.blockNoise(seed = 40)),
        )
        val whileBlurry = controller.onFrame(blurry, nowMillis = 1_500)
        val afterFocus = controller.onFrame(interior(), nowMillis = 2_500)

        assertIs<DirectedCaptureController.State.Requesting>(whileBlurry)
        assertIs<DirectedCaptureController.State.Captured>(afterFocus)
    }

    @Test
    fun `antes del tiempo minimo de reorientacion no se captura`() {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)
        controller.onFrame(exterior(), nowMillis = 0)

        val tooSoon = controller.onFrame(
            interior(),
            nowMillis = DirectedCaptureController.DEFAULT_MIN_AIM_MILLIS - 1,
        )

        assertIs<DirectedCaptureController.State.Requesting>(tooSoon)
    }

    @Test
    fun `el frame capturado sobrevive a la reutilizacion del bufer`() {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)
        controller.onFrame(exterior(), nowMillis = 0)

        val interiorFrame = interior()
        val state = controller.onFrame(interiorFrame, nowMillis = 1_500)

        interiorFrame.luma.fill(0)

        val captured = assertIs<DirectedCaptureController.State.Captured>(state)
        val capturedLuma = (captured.frame as LumaImageFrame).luma
        assertEquals(
            true,
            capturedLuma.any { it != 0.toByte() },
            "La captura debe ser una copia: el anillo de búferes se reutiliza",
        )
    }

    @Test
    fun `sin toma dentro del plazo se pasa a NotProvided`() {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)
        controller.onFrame(exterior(), nowMillis = 0)

        val state = controller.onFrame(
            exterior(),
            nowMillis = DirectedCaptureController.DEFAULT_TIMEOUT_MILLIS,
        )

        val notProvided = assertIs<DirectedCaptureController.State.NotProvided>(state)
        assertEquals("inspection.point_inside", notProvided.promptKey)
    }

    @Test
    fun `cancel devuelve el controlador a Inactive`() {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)

        controller.cancel()

        assertIs<DirectedCaptureController.State.Inactive>(controller.state)
    }

    @Test
    fun `la vista capturada se entrega al puerto de contaminacion`() = runTest {
        val expected = ContaminationResult(ContaminationState.CONTAMINATED, confidence = 0.88f)
        val classifier = FakeWasteClassifier(contamination = expected)
        val controller = controller()
        controller.begin(rule, nowMillis = 0)
        controller.onFrame(exterior(), nowMillis = 0)
        val state = controller.onFrame(interior(), nowMillis = 1_500)

        val result = state.deliverTo(classifier)

        assertEquals(expected, result)
    }

    @Test
    fun `sin toma la entrega devuelve null y aplica la ruta conservadora`() = runTest {
        val controller = controller()
        controller.begin(rule, nowMillis = 0)
        controller.onFrame(exterior(), nowMillis = 0)
        val state = controller.onFrame(
            exterior(),
            nowMillis = DirectedCaptureController.DEFAULT_TIMEOUT_MILLIS,
        )

        val result = state.deliverTo(FakeWasteClassifier())

        assertNull(result, "Sin resultado, el llamador resuelve con UNKNOWN: ruta conservadora")
    }
}
