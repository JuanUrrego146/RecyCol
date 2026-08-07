package com.recycol.testing

import com.recycol.domain.model.ClassificationRecord
import com.recycol.domain.port.ClassificationHistoryRepository

/**
 * Fake determinista de `ClassificationHistoryRepository` (implementación real:
 * agente DATA, S37). Lista en memoria, ordenada de más reciente a más antiguo
 * según el orden de registro; el borrado es efectivo.
 */
class FakeClassificationHistoryRepository : ClassificationHistoryRepository {

    private val records = mutableListOf<ClassificationRecord>()

    override suspend fun record(record: ClassificationRecord) {
        records.add(0, record)
    }

    override suspend fun history(): List<ClassificationRecord> = records.toList()

    override suspend fun clear() {
        records.clear()
    }
}
