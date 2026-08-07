package com.recycol.data.history

import com.recycol.data.db.RecyColDatabase
import com.recycol.domain.model.BinId
import com.recycol.domain.model.ClassificationRecord
import com.recycol.domain.model.WasteMaterial
import com.recycol.domain.port.ClassificationHistoryRepository

/**
 * Implementación SQLDelight de `ClassificationHistoryRepository`
 * (S37; CUS-009, RF-032 a RF-034).
 *
 * Persiste únicamente el resultado de cada clasificación —material, caneca y
 * momento—. El puerto no acepta frames y el esquema no admite datos binarios,
 * de modo que ninguna imagen puede llegar a disco por esta vía (RNF-012);
 * la prueba de privacidad del módulo lo verifica sobre el archivo real.
 */
class SqlDelightClassificationHistoryRepository(
    database: RecyColDatabase,
) : ClassificationHistoryRepository {

    private val queries = database.classificationRecordQueries

    override suspend fun record(record: ClassificationRecord) {
        queries.insertRecord(
            id = record.id,
            material = record.material.name,
            bin_id = record.binId.value,
            timestamp_millis = record.timestampMillis,
        )
    }

    override suspend fun history(): List<ClassificationRecord> =
        queries.selectAllByRecency { id, material, binId, timestampMillis ->
            ClassificationRecord(
                id = id,
                material = WasteMaterial.valueOf(material),
                binId = BinId(binId),
                timestampMillis = timestampMillis,
            )
        }.executeAsList()

    override suspend fun clear() {
        queries.deleteAll()
    }
}
