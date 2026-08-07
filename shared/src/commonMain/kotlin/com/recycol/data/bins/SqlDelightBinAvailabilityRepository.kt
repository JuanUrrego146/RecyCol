package com.recycol.data.bins

import com.recycol.data.db.RecyColDatabase
import com.recycol.domain.model.BinId
import com.recycol.domain.port.BinAvailabilityRepository

/**
 * Implementación SQLDelight de `BinAvailabilityRepository` (S36; CUS-002, RF-007).
 *
 * Guarda únicamente los identificadores de caneca confirmados por el usuario;
 * la definición completa —color, nombre, ruta— vive en el perfil normativo
 * activo. Guardar una selección reemplaza la anterior de forma atómica.
 */
class SqlDelightBinAvailabilityRepository(
    database: RecyColDatabase,
) : BinAvailabilityRepository {

    private val queries = database.availableBinQueries

    override suspend fun availableBins(): Set<BinId> =
        queries.selectAll().executeAsList().map(::BinId).toSet()

    override suspend fun saveAvailableBins(bins: Set<BinId>) {
        queries.transaction {
            queries.deleteAll()
            bins.forEach { queries.insertBin(it.value) }
        }
    }
}
