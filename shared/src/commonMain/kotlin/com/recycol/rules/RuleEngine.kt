package com.recycol.rules

import com.recycol.domain.model.BinId
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.Disposal
import com.recycol.domain.model.WasteMaterial

/**
 * Motor de reglas normativas: el único lugar del sistema donde un material se
 * convierte en caneca. Ni el clasificador, ni la UI, ni los repositorios pueden
 * decidir una caneca por su cuenta.
 *
 * Lo implementa el agente RULES en `shared/rules/` evaluando el perfil
 * normativo activo (datos, nunca código: RNF-004). Contrato inmutable desde M0.
 */
interface RuleEngine {

    /**
     * Resuelve la caneca destino para un material dado su estado de
     * contaminación, restringido a las canecas realmente disponibles.
     *
     * @param material material predicho por el clasificador.
     * @param contamination estado de contaminación conocido; [ContaminationState.UNKNOWN]
     *   si la inspección no ha ocurrido.
     * @param availableBins canecas registradas en el entorno del usuario; un
     *   conjunto vacío significa «sin restricción» y se resuelve contra todas
     *   las canecas del perfil.
     * @param profile perfil normativo del país activo.
     * @return decisión con caneca, ruta, justificación citable y si la decisión
     *   se degradó por contaminación. Si la caneca ideal no está disponible se
     *   propone la alternativa más conservadora que declare el perfil.
     */
    fun resolve(
        material: WasteMaterial,
        contamination: ContaminationState,
        availableBins: Set<BinId>,
        profile: CountryProfile,
    ): Disposal
}
