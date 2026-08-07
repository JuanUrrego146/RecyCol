package com.recycol.testing

import com.recycol.domain.model.BinId
import com.recycol.domain.model.ClassificationRecord
import com.recycol.domain.model.WasteMaterial
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Documenta el contrato del fake de `ClassificationHistoryRepository`:
 * orden de más reciente a más antiguo y borrado efectivo.
 */
class FakeClassificationHistoryRepositoryTest {

    private fun record(id: String, timestamp: Long) = ClassificationRecord(
        id = id,
        material = WasteMaterial.PLASTIC,
        binId = BinId("white"),
        timestampMillis = timestamp,
    )

    @Test
    fun elHistorialDevuelveLoMasRecientePrimero() = runTest {
        val repository = FakeClassificationHistoryRepository()

        repository.record(record(id = "primero", timestamp = 1_000L))
        repository.record(record(id = "segundo", timestamp = 2_000L))

        assertEquals(listOf("segundo", "primero"), repository.history().map { it.id })
    }

    @Test
    fun elBorradoEsEfectivo() = runTest {
        val repository = FakeClassificationHistoryRepository()
        repository.record(record(id = "unico", timestamp = 1_000L))

        repository.clear()

        assertTrue(repository.history().isEmpty())
    }
}
