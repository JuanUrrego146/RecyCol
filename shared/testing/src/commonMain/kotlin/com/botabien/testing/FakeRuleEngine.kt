package com.botabien.testing

import com.botabien.domain.model.BinId
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.Disposal
import com.botabien.domain.model.WasteMaterial
import com.botabien.rules.RuleEngine

/**
 * Fake determinista de `RuleEngine` (implementación real: agente RULES, S29).
 *
 * Reproduce la semántica mínima del contrato para que los consumidores puedan
 * probar sus flujos completos:
 *
 * 1. Se busca la regla del material; sin regla, aplica la caneca conservadora.
 * 2. Si el residuo está [ContaminationState.CONTAMINATED] y la regla declara
 *    alternativa, la decisión se degrada a esa alternativa.
 * 3. Si la caneca ideal no está entre las disponibles, se cae a la caneca
 *    conservadora del perfil. Un conjunto vacío significa «sin restricción».
 *
 * No implementa la casuística fina de la resolución real (esa vive en S29);
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

        val degraded = rule?.contaminatedFallback != null &&
            contamination == ContaminationState.CONTAMINATED

        val idealBin = when {
            rule == null -> profile.conservativeBin
            degraded -> rule.contaminatedFallback ?: rule.targetBin
            else -> rule.targetBin
        }

        val resolvedBinId = if (availableBins.isEmpty() || idealBin in availableBins) {
            idealBin
        } else {
            profile.conservativeBin
        }

        val bin = profile.bins.first { it.id == resolvedBinId }
        return Disposal(
            bin = bin,
            route = bin.route,
            justification = rule?.justification ?: profile.regulationName,
            degradedByContamination = degraded,
        )
    }
}
