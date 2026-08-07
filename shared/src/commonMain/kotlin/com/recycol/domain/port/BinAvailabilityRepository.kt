package com.recycol.domain.port

import com.recycol.domain.model.BinId

/**
 * Puerto del conjunto de canecas disponibles en el entorno del usuario
 * (CUS-002). Lo implementa el agente DATA; lo alimenta el flujo de escaneo
 * y confirmación de canecas. Contrato del M0 ampliado por la issue #48.
 */
interface BinAvailabilityRepository {

    /**
     * Canecas confirmadas por el usuario. Un conjunto vacío significa
     * «sin restricción»: se resuelve contra todas las canecas del perfil.
     */
    suspend fun availableBins(): Set<BinId>

    /** Persiste el conjunto confirmado de canecas disponibles. */
    suspend fun saveAvailableBins(bins: Set<BinId>)
}
