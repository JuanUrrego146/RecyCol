package com.recycol.testing

import com.recycol.domain.model.BinId
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.Disposal
import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.model.FallbackReason
import com.recycol.domain.model.WasteMaterial
import com.recycol.rules.RuleEngine

/**
 * Fake determinista de `RuleEngine` (implementación real: agente RULES, S29/S32).
 *
 * Reproduce la semántica mínima del contrato para que los consumidores puedan
 * probar sus flujos completos:
 *
 * 1. Se busca la regla del material; sin regla, aplica la caneca conservadora.
 * 2. Si el residuo está [ContaminationState.CONTAMINATED] y la regla declara
 *    alternativa, la decisión se degrada a esa alternativa
 *    ([FallbackReason.CONTAMINATION]).
 * 3. Si la caneca ideal no está entre las disponibles, se cae a la caneca
 *    conservadora ([FallbackReason.UNAVAILABLE_BIN]); si ocurren ambas cosas,
 *    se reporta el último salto. Un conjunto vacío significa «sin restricción».
 * 4. Los destinos con ruta [DisposalRoute.SPECIAL_COLLECTION] están exentos de
 *    la restricción por disponibilidad (decisión de Juan en #54): un punto de
 *    recolección no es una caneca del entorno y jamás se degrada a otra caneca.
 *
 * No implementa la casuística fina de la resolución real (S29/S32);
 * su valor es ser predecible y estable.
 */
class FakeRuleEngine : RuleEngine {

    override fun resolve(
        material: WasteMaterial,
        contamination: ContaminationState,
        availableBins: Set<BinId>,
        profile: CountryProfile,
    ): Disposal {
        val rule = profile.rules.firstOrNull { it.material == material }

        val degradedByContamination = rule?.contaminatedFallback != null &&
            contamination == ContaminationState.CONTAMINATED

        val idealBinId = when {
            rule == null -> profile.conservativeBin
            degradedByContamination -> rule.contaminatedFallback ?: rule.targetBin
            else -> rule.targetBin
        }
        val idealBin = profile.bins.first { it.id == idealBinId }

        val exemptFromAvailability = idealBin.route == DisposalRoute.SPECIAL_COLLECTION
        val unavailable = !exemptFromAvailability &&
            availableBins.isNotEmpty() &&
            idealBinId !in availableBins

        val resolvedBin = if (unavailable) {
            profile.bins.first { it.id == profile.conservativeBin }
        } else {
            idealBin
        }

        // Aviso de caneca ausente (RF-008, #61): plantilla del perfil con los
        // marcadores sustituidos por los nombres visibles de las canecas.
        val notice = if (unavailable && profile.unavailableBinNotice.isNotEmpty()) {
            profile.unavailableBinNotice
                .replace("{ideal}", idealBin.displayName)
                .replace("{assigned}", resolvedBin.displayName)
        } else {
            null
        }

        return Disposal(
            bin = resolvedBin,
            route = resolvedBin.route,
            justification = rule?.justification ?: profile.regulationName,
            degradedByContamination = degradedByContamination,
            fallbackReason = when {
                unavailable -> FallbackReason.UNAVAILABLE_BIN
                degradedByContamination -> FallbackReason.CONTAMINATION
                else -> FallbackReason.NONE
            },
            unavailableBinNotice = notice,
        )
    }
}
