package com.recycol.rules

import com.recycol.domain.model.BinDefinition
import com.recycol.domain.model.BinId
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.Disposal
import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.model.FallbackReason
import com.recycol.domain.model.WasteMaterial

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
 * 4. La decisión se restringe a las canecas disponibles (RF-008): un conjunto
 *    vacío significa «sin restricción». Si la caneca ideal no está disponible
 *    se propone la conservadora del perfil y, si tampoco está, la disponible
 *    de ruta más conservadora. La reasignación se señala con
 *    [FallbackReason.UNAVAILABLE_BIN] y el aviso del perfil
 *    ([CountryProfile.unavailableBinNotice]) viaja ya renderizado en
 *    [Disposal.unavailableBinNotice] (coordinaciones #61 y #78).
 * 5. Una caneca ideal de ruta [DisposalRoute.SPECIAL_COLLECTION] queda exenta
 *    de la restricción: el punto de recolección especial no es una caneca del
 *    entorno escaneado y la recomendación de llevarlo allí no se degrada
 *    (coordinación #54: pilas y aparatos electrónicos).
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
            ?: return conservativeDisposal(profile, availableBins)

        val unverifiedInspection = contamination == ContaminationState.UNKNOWN &&
            profile.requiresInspection(material)
        val degraded = rule.contaminatedFallback != null &&
            (contamination == ContaminationState.CONTAMINATED || unverifiedInspection)

        val ideal = profile.requireBin(if (degraded) rule.contaminatedFallback!! else rule.targetBin)
        val assigned = restrictToAvailable(ideal, availableBins, profile)

        return disposal(ideal, assigned, rule.justification, degraded, profile)
    }

    /** Decisión para un material que el perfil no contempla: ante la duda, la caneca conservadora. */
    private fun conservativeDisposal(profile: CountryProfile, availableBins: Set<BinId>): Disposal {
        val ideal = profile.requireBin(profile.conservativeBin)
        val assigned = restrictToAvailable(ideal, availableBins, profile)
        return disposal(ideal, assigned, profile.regulationReference, degraded = false, profile)
    }

    /**
     * Arma la decisión final con las señales del contrato (#78): si la caneca
     * asignada no es la ideal manda [FallbackReason.UNAVAILABLE_BIN] con el
     * aviso del perfil renderizado; si no, la degradación por contaminación.
     * [Disposal.degradedByContamination] conserva su señal aunque además haya
     * reasignación por disponibilidad.
     */
    private fun disposal(
        ideal: BinDefinition,
        assigned: BinDefinition,
        justification: String,
        degraded: Boolean,
        profile: CountryProfile,
    ): Disposal {
        val reassigned = assigned.id != ideal.id
        return Disposal(
            bin = assigned,
            route = assigned.route,
            justification = justification,
            degradedByContamination = degraded,
            fallbackReason = when {
                reassigned -> FallbackReason.UNAVAILABLE_BIN
                degraded -> FallbackReason.CONTAMINATION
                else -> FallbackReason.NONE
            },
            unavailableBinNotice = if (reassigned) renderNotice(profile, ideal, assigned) else null,
        )
    }

    /** Aviso del perfil con los marcadores sustituidos, o `null` si no declara plantilla. */
    private fun renderNotice(
        profile: CountryProfile,
        ideal: BinDefinition,
        assigned: BinDefinition,
    ): String? = profile.unavailableBinNotice.takeIf { it.isNotBlank() }
        ?.replace(IDEAL_PLACEHOLDER, ideal.displayName)
        ?.replace(ASSIGNED_PLACEHOLDER, assigned.displayName)

    /**
     * Restringe la caneca ideal al conjunto realmente disponible en el entorno
     * (RF-008). Un conjunto vacío significa «sin restricción» (contrato de
     * [RuleEngine]); un conjunto que no contiene ninguna caneca del perfil se
     * trata igual, porque no describe ningún entorno utilizable. Si la ideal
     * no está disponible se prefiere la caneca conservadora del perfil y, en
     * su ausencia, la disponible de ruta más conservadora.
     */
    private fun restrictToAvailable(
        ideal: BinDefinition,
        availableBins: Set<BinId>,
        profile: CountryProfile,
    ): BinDefinition {
        // Exenta de restricción (#54): el punto de recolección especial no es
        // una caneca del entorno escaneado; la recomendación no se degrada.
        if (ideal.route == DisposalRoute.SPECIAL_COLLECTION) return ideal
        if (availableBins.isEmpty() || ideal.id in availableBins) return ideal

        val usable = profile.bins.filter { it.id in availableBins }
        if (usable.isEmpty()) return ideal

        return usable.firstOrNull { it.id == profile.conservativeBin }
            ?: usable.minBy { conservatismRank(it.route) }
    }

    /**
     * Orden de conservadurismo entre rutas cuando ni la caneca ideal ni la
     * conservadora del perfil están disponibles: primero las corrientes que
     * no contaminan ninguna corriente de aprovechamiento y las que pasan por
     * clasificación posterior controlada; de último la orgánica, cuya calidad
     * de compostaje es la más sensible a un residuo mal ubicado. El desempate
     * lo da el orden de declaración de las canecas en el perfil.
     */
    private fun conservatismRank(route: DisposalRoute): Int = when (route) {
        DisposalRoute.NON_RECYCLABLE -> 0
        DisposalRoute.SPECIAL_COLLECTION -> 1
        DisposalRoute.HAZARDOUS -> 2
        DisposalRoute.RECYCLABLE -> 3
        DisposalRoute.ORGANIC -> 4
    }

    private fun CountryProfile.requireBin(id: BinId): BinDefinition =
        requireNotNull(bins.firstOrNull { it.id == id }) {
            "El perfil «$isoCode» referencia la caneca «${id.value}» pero no la define; " +
                "el perfil debió rechazarse en la validación de carga"
        }

    private companion object {
        /** Marcadores de [CountryProfile.unavailableBinNotice]. */
        const val IDEAL_PLACEHOLDER = "{ideal}"
        const val ASSIGNED_PLACEHOLDER = "{assigned}"
    }
}
