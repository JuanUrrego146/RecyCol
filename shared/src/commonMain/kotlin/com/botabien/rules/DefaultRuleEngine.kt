package com.botabien.rules

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.Disposal
import com.botabien.domain.model.WasteMaterial

/**
 * Implementación de [RuleEngine] que evalúa el perfil normativo activo (RF-012, CUS-003).
 *
 * Toda la decisión sale de los datos del perfil, nunca de código condicionado
 * por país (RNF-004):
 *
 * 1. Se busca la [regla del material][CountryProfile.rules]; un material sin
 *    regla cae en la caneca conservadora del perfil.
 * 2. Si el residuo está [ContaminationState.CONTAMINATED] y la regla declara
 *    caneca alternativa, la decisión se degrada a esa alternativa. Una regla
 *    sin alternativa significa que la contaminación no cambia el destino.
 * 3. Si el perfil declara [regla de inspección][CountryProfile.inspectionRules]
 *    para el material y el estado sigue [ContaminationState.UNKNOWN] —no se
 *    pudo verificar el interior—, el sistema no adivina: aplica la misma
 *    degradación conservadora que si estuviera contaminado (RF-019, RF-022).
 *    Sin regla de inspección, el estado desconocido resuelve como limpio.
 * 4. La decisión se restringe a las canecas disponibles: un conjunto vacío
 *    significa «sin restricción»; si la caneca ideal no está disponible se
 *    cae en la caneca conservadora del perfil.
 *
 * La justificación de cada decisión es la de la regla aplicada: dato citable
 * del perfil, nunca un literal de código (RNF-011). Para un material sin regla
 * se cita la referencia de la norma del perfil.
 */
class DefaultRuleEngine : RuleEngine {

    override fun resolve(
        material: WasteMaterial,
        contamination: ContaminationState,
        availableBins: Set<BinId>,
        profile: CountryProfile,
    ): Disposal {
        val rule = profile.rules.firstOrNull { it.material == material }
            ?: return conservativeDisposal(profile)

        val unverifiedInspection = contamination == ContaminationState.UNKNOWN &&
            profile.requiresInspection(material)
        val degraded = rule.contaminatedFallback != null &&
            (contamination == ContaminationState.CONTAMINATED || unverifiedInspection)

        val idealBin = if (degraded) rule.contaminatedFallback!! else rule.targetBin
        val resolvedBin = restrictToAvailable(idealBin, availableBins, profile)

        val bin = profile.requireBin(resolvedBin)
        return Disposal(
            bin = bin,
            route = bin.route,
            justification = rule.justification,
            degradedByContamination = degraded,
        )
    }

    /** Decisión para un material que el perfil no contempla: ante la duda, la caneca conservadora. */
    private fun conservativeDisposal(profile: CountryProfile): Disposal {
        val bin = profile.requireBin(profile.conservativeBin)
        return Disposal(
            bin = bin,
            route = bin.route,
            justification = profile.regulationReference,
            degradedByContamination = false,
        )
    }

    /**
     * Restringe la caneca ideal al conjunto realmente disponible en el entorno.
     * Un conjunto vacío significa «sin restricción» (contrato de [RuleEngine]).
     */
    private fun restrictToAvailable(
        ideal: BinId,
        availableBins: Set<BinId>,
        profile: CountryProfile,
    ): BinId = if (availableBins.isEmpty() || ideal in availableBins) {
        ideal
    } else {
        profile.conservativeBin
    }

    private fun CountryProfile.requireBin(id: BinId): BinDefinition =
        requireNotNull(bins.firstOrNull { it.id == id }) {
            "El perfil «$isoCode» referencia la caneca «${id.value}» pero no la define; " +
                "el perfil debió rechazarse en la validación de carga"
        }
}
