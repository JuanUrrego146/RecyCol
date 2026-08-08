package com.recycol.domain.usecase

import com.recycol.domain.model.CaptureHint
import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.model.WasteMaterial

/**
 * Lo que una pasada deja para la pantalla.
 *
 * @property decision decisión nueva, o `null` si la pantalla no cambia.
 * @property hints indicaciones de captura de esta pasada. Van siempre: son del
 *   fotograma, no de la decisión, y tienen que responder rápido.
 */
data class TrackedFrame(
    val decision: StabilizedDecision?,
    val hints: List<CaptureHint>,
)

/**
 * Seguimiento de un objeto a lo largo de una sesión de cámara (CUS-003 a
 * CUS-006).
 *
 * Es el único componente con estado del flujo de clasificación y tiene **ciclo de
 * vida por pantalla**: se crea al entrar, se reinicia al salir. Envuelve a
 * [ClassifyWasteUseCase], que sigue siendo sin estado y reentrante — sus pruebas
 * no saben que esta clase existe.
 *
 * También se lleva aquí el enrutado de la inspección interior que estaba en la
 * pantalla: era el último trozo de lógica de decisión en la interfaz y el único
 * motivo por el que el estado de pantalla necesitaba un reloj propio.
 */
class TrackClassificationUseCase(
    private val classifyWaste: ClassifyWasteUseCase,
    private val stabilizer: ClassificationStabilizer = ClassificationStabilizer(),
    /**
     * Plan B: la etapa de contaminación se entrenó con suciedad sintética y marca
     * como limpio el 98,75 % de la suciedad real, así que se pregunta al usuario
     * en vez de resolverlo. Cuando la detección funcione, basta con pasar `false`
     * y el flujo de CUS-005 revive intacto.
     */
    private val askUserAboutContamination: Boolean = true,
    private val interiorGraceMillis: Long = INTERIOR_GRACE_MS,
) {

    private var awaitingMaterial: WasteMaterial? = null
    private var awaitingSince = 0L

    /** Hipótesis vivas para sembrar la hoja de selección manual (CUS-006). */
    fun candidates(): List<WasteMaterial> = stabilizer.candidates()

    /** `true` mientras la decisión visible sea la opinión de un solo fotograma. */
    val isProvisional: Boolean get() = stabilizer.isProvisional

    suspend fun onFrame(frame: ImageFrame): TrackedFrame {
        val pending = awaitingMaterial
        if (pending != null && frame.timestampMillis - awaitingSince >= interiorGraceMillis) {
            awaitingMaterial = null
            val resolved = classifyWaste.resolveContamination(pending, frame)
            // Una inspección concluyente es evidencia dirigida sobre este objeto
            // concreto, no una papeleta más: vale tanto como la respuesta de una
            // persona y se fija igual.
            val decision = if (resolved.disposal != null) {
                stabilizer.pin(resolved)
            } else {
                stabilizer.offer(resolved, frame.timestampMillis, luminance = null)
            }
            return TrackedFrame(decision, resolved.hints)
        }

        val evaluation = classifyWaste.evaluate(frame)
        val decision = stabilizer.offer(
            outcome = evaluation.outcome,
            atMillis = frame.timestampMillis,
            luminance = evaluation.quality.luminance,
        )

        if (!askUserAboutContamination &&
            awaitingMaterial == null &&
            CaptureHint.POINT_INSIDE in stabilizer.visibleHints
        ) {
            // Del estado visible, no de `decision`: la mayoría de las pasadas no
            // emiten nada y leer de ahí dejaría la toma dirigida sin arrancar
            // nunca salvo en el fotograma exacto del cambio.
            awaitingMaterial = stabilizer.visibleOutcome?.classification?.material
            awaitingSince = frame.timestampMillis
        }

        // La directiva de inspección la aporta la decisión estable; las
        // indicaciones de calidad, el fotograma. Van juntas y en ese orden porque
        // arreglar la toma es más urgente que apuntar hacia adentro.
        val frameHints = evaluation.outcome.hints.filterNot { it == CaptureHint.POINT_INSIDE }
        val hints = if (CaptureHint.POINT_INSIDE in stabilizer.visibleHints) {
            frameHints + CaptureHint.POINT_INSIDE
        } else {
            frameHints
        }
        return TrackedFrame(decision, hints)
    }

    /** El usuario se pronunció: su decisión pasa a mandar (CUS-006). */
    fun applyUserDecision(outcome: ClassificationOutcome): StabilizedDecision =
        stabilizer.pin(outcome)

    /** Fin de la sesión de cámara: se olvida todo menos el epoch. */
    fun reset() {
        awaitingMaterial = null
        awaitingSince = 0L
        stabilizer.reset()
    }

    companion object {
        /** Gracia para que el usuario reoriente la cámara hacia el interior. */
        const val INTERIOR_GRACE_MS = 1_500L
    }
}
