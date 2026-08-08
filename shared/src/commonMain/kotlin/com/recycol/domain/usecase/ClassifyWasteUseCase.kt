package com.recycol.domain.usecase

import com.recycol.domain.model.CaptureHint
import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.FrameQuality
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.model.WasteMaterial
import com.recycol.domain.port.BinAvailabilityRepository
import com.recycol.domain.port.FrameQualityAnalyzer
import com.recycol.domain.port.ProfileRepository
import com.recycol.domain.port.WasteClassifier
import com.recycol.rules.RuleEngine

/**
 * Caso de uso central: clasificar un residuo con la cámara (CUS-003, CUS-004,
 * CUS-005, CUS-006).
 *
 * Orquesta calidad → clasificación → umbral → reglas. Los ViewModels lo
 * invocan y jamás replican esta lógica (invariante 4 de la arquitectura);
 * la caneca la decide siempre el RuleEngine (invariante 2).
 *
 * Flujo en dos pasos cuando el material tiene regla de inspección:
 * [execute] devuelve la decisión preliminar con la indicación
 * [CaptureHint.POINT_INSIDE]; el ViewModel captura la vista interior y llama
 * a [resolveContamination] para la decisión definitiva.
 */
class ClassifyWasteUseCase(
    private val qualityAnalyzer: FrameQualityAnalyzer,
    private val classifier: WasteClassifier,
    private val ruleEngine: RuleEngine,
    private val profiles: ProfileRepository,
    private val binAvailability: BinAvailabilityRepository,
    private val thresholds: ConfidenceThresholds = ConfidenceThresholds(),
    private val qualityThresholds: QualityThresholds = QualityThresholds(),
) {

    /**
     * Pasada principal sobre un frame de cámara.
     *
     * - Calidad insuficiente → indicaciones de captura, sin decisión.
     * - Confianza bajo el umbral → la app no adivina: decisión del usuario (RF-023).
     * - Material con regla de inspección → decisión preliminar + [CaptureHint.POINT_INSIDE].
     * - En el resto de casos → decisión definitiva.
     */
    suspend fun execute(frame: ImageFrame): ClassificationOutcome = evaluate(frame).outcome

    /**
     * Igual que [execute], pero conserva la calidad medida del frame.
     *
     * El caso de uso sigue siendo **sin estado**: quien agrega frames a lo largo
     * del tiempo es [TrackClassificationUseCase], que se instancia por sesión de
     * pantalla. Aquí no se recuerda nada entre llamadas y el perfil se resuelve en
     * cada pasada.
     */
    suspend fun evaluate(frame: ImageFrame): FrameEvaluation {
        val quality = qualityAnalyzer.analyze(frame)
        val hints = captureHintsFor(quality)
        if (hints.isNotEmpty()) {
            return FrameEvaluation(
                ClassificationOutcome(
                    classification = null,
                    disposal = null,
                    hints = hints,
                    needsUserDecision = false,
                ),
                quality,
            )
        }

        val classification = classifier.classify(frame)
        if (classification.confidence < thresholds.material) {
            return FrameEvaluation(
                ClassificationOutcome(
                    classification = classification,
                    disposal = null,
                    hints = emptyList(),
                    needsUserDecision = true,
                ),
                quality,
            )
        }

        val profile = activeProfile()
        val disposal = ruleEngine.resolve(
            material = classification.material,
            contamination = ContaminationState.UNKNOWN,
            availableBins = binAvailability.availableBins(),
            profile = profile,
        )

        val requiresInspection = profile.inspectionRules.any { it.material == classification.material }
        return FrameEvaluation(
            ClassificationOutcome(
                classification = classification,
                disposal = disposal,
                hints = if (requiresInspection) listOf(CaptureHint.POINT_INSIDE) else emptyList(),
                needsUserDecision = false,
            ),
            quality,
        )
    }

    /**
     * Segundo paso del flujo de inspección: evalúa la contaminación sobre la
     * toma dirigida y produce la decisión definitiva.
     *
     * Si la inspección no es concluyente (confianza bajo el umbral), la app no
     * adivina: devuelve `needsUserDecision = true` sin decisión (RF-023).
     */
    suspend fun resolveContamination(
        material: WasteMaterial,
        interiorFrame: ImageFrame,
    ): ClassificationOutcome {
        val contamination = classifier.inspectContamination(interiorFrame)
        val classification = ClassificationResult(material, contamination.confidence)

        if (contamination.confidence < thresholds.contamination ||
            contamination.state == ContaminationState.UNKNOWN
        ) {
            return ClassificationOutcome(
                classification = classification,
                disposal = null,
                hints = emptyList(),
                needsUserDecision = true,
            )
        }

        val disposal = ruleEngine.resolve(
            material = material,
            contamination = contamination.state,
            availableBins = binAvailability.availableBins(),
            profile = activeProfile(),
        )
        return ClassificationOutcome(
            classification = classification,
            disposal = disposal,
            hints = emptyList(),
            needsUserDecision = false,
        )
    }

    private suspend fun activeProfile(): CountryProfile =
        checkNotNull(profiles.activeProfileOrNull()) {
            "No hay perfil normativo activo: el onboarding de selección de país no se completó"
        }

    private fun captureHintsFor(quality: FrameQuality): List<CaptureHint> = buildList {
        if (quality.lensSoiling) add(CaptureHint.CLEAN_LENS)
        if (quality.luminance < qualityThresholds.luminance) add(CaptureHint.MORE_LIGHT)
        if (quality.sharpness < qualityThresholds.sharpness) add(CaptureHint.MOVE_CLOSER)
        if (!quality.objectCentered) add(CaptureHint.CENTER_OBJECT)
    }
}
