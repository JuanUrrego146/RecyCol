package com.recycol.testing

import com.recycol.domain.model.BinId
import com.recycol.domain.port.BinAvailabilityRepository

/**
 * Fake determinista de `BinAvailabilityRepository` (implementación real:
 * agente DATA). Conjunto en memoria; por defecto vacío, que en el contrato
 * significa «sin restricción».
 */
class FakeBinAvailabilityRepository(
    initialBins: Set<BinId> = emptySet(),
) : BinAvailabilityRepository {

    private var bins: Set<BinId> = initialBins

    override suspend fun availableBins(): Set<BinId> = bins

    override suspend fun saveAvailableBins(bins: Set<BinId>) {
        this.bins = bins
    }
}
