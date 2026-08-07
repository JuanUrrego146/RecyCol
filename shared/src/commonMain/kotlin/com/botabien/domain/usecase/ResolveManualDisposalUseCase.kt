package com.botabien.domain.usecase

import com.botabien.domain.model.ClassificationOutcome
import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.WasteMaterial
import com.botabien.domain.port.BinAvailabilityRepository
import com.botabien.domain.port.ProfileRepository
import com.botabien.rules.RuleEngine

/**
 * Caso de uso de selección manual de material (RF-024, RF-025 · CUS-006).
 *
 * Cubre la desambiguación de baja confianza y la elección directa del usuario
 * (por ejemplo «es un aparato electrónico» → punto de recolección especial,
 * decisión de Juan en #54). La caneca la decide siempre el RuleEngine con el
 * perfil activo y las canecas disponibles; aquí no hay tabla propia.
 *
 * Para materiales con regla de inspección (cartón para bebidas), la UI debe
 * preguntar al usuario por la contaminación («¿tiene residuos adentro?») y
 * pasar [ContaminationState.CLEAN] o [ContaminationState.CONTAMINATED];
 * con [ContaminationState.UNKNOWN] el motor resuelve por la regla sin degradar.
 */
class ResolveManualDisposalUseCase(
    private val ruleEngine: RuleEngine,
    private val profiles: ProfileRepository,
    private val binAvailability: BinAvailabilityRepository,
) {

    /**
     * Resuelve la caneca para un material elegido a mano por el usuario.
     * El resultado queda marcado con `manualSelection = true` y confianza
     * plena: la afirmación del usuario no se pondera como una predicción.
     */
    suspend fun resolve(
        material: WasteMaterial,
        contamination: ContaminationState = ContaminationState.UNKNOWN,
    ): ClassificationOutcome {
        val profile = checkNotNull(profiles.activeProfileOrNull()) {
            "No hay perfil normativo activo: el onboarding de selección de país no se completó"
        }
        val disposal = ruleEngine.resolve(
            material = material,
            contamination = contamination,
            availableBins = binAvailability.availableBins(),
            profile = profile,
        )
        return ClassificationOutcome(
            classification = ClassificationResult(material, MANUAL_CONFIDENCE),
            disposal = disposal,
            hints = emptyList(),
            needsUserDecision = false,
            manualSelection = true,
        )
    }

    companion object {
        /** La selección manual es una afirmación del usuario, no una predicción. */
        private const val MANUAL_CONFIDENCE = 1.0f
    }
}
