package com.recycol.domain.port

import com.recycol.domain.model.ClassificationRecord

/**
 * Puerto del historial local de clasificaciones (CUS-009, RF-032 a RF-034).
 *
 * Lo implementa el agente DATA con SQLDelight (S37). Solo persiste resultados:
 * jamás frames (RNF-012). El borrado es efectivo e irreversible. Contrato del
 * M0 ampliado por la issue #48.
 */
interface ClassificationHistoryRepository {

    /** Registra una clasificación en el historial. */
    suspend fun record(record: ClassificationRecord)

    /** Historial completo, ordenado de más reciente a más antiguo. */
    suspend fun history(): List<ClassificationRecord>

    /** Borra todo el historial de forma efectiva. */
    suspend fun clear()
}
