package com.recycol.domain.usecase

import com.recycol.domain.model.ClassificationRecord
import com.recycol.domain.port.ClassificationHistoryRepository

/**
 * Caso de uso de consulta y borrado del historial local (RF-033, RF-034 ·
 * CUS-009, coordinación #94). El registro de entradas nuevas es parte del
 * flujo de resultado (S37, agente DATA); aquí solo consulta y borrado.
 */
class ManageHistoryUseCase(
    private val history: ClassificationHistoryRepository,
) {

    /** Historial completo, de más reciente a más antiguo. Nunca contiene frames (RNF-012). */
    suspend fun entries(): List<ClassificationRecord> = history.history()

    /** Borra todo el historial de forma efectiva e irreversible (RF-034). */
    suspend fun clear() {
        history.clear()
    }
}
