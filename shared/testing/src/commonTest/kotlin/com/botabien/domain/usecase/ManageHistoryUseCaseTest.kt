package com.botabien.domain.usecase

import com.botabien.domain.model.BinId
import com.botabien.domain.model.ClassificationRecord
import com.botabien.domain.model.WasteMaterial
import com.botabien.testing.FakeClassificationHistoryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pruebas de consulta y borrado del historial (RF-033/RF-034, coordinación #94). */
class ManageHistoryUseCaseTest {

    @Test
    fun consultaElHistorialDeMasRecienteAMasAntiguo() = runTest {
        val repository = FakeClassificationHistoryRepository()
        repository.record(ClassificationRecord("a", WasteMaterial.PLASTIC, BinId("white"), 1_000L))
        repository.record(ClassificationRecord("b", WasteMaterial.ORGANIC, BinId("green"), 2_000L))

        val entries = ManageHistoryUseCase(repository).entries()

        assertEquals(listOf("b", "a"), entries.map { it.id })
    }

    @Test
    fun elBorradoEsEfectivo() = runTest {
        val repository = FakeClassificationHistoryRepository()
        repository.record(ClassificationRecord("a", WasteMaterial.PLASTIC, BinId("white"), 1_000L))
        val useCase = ManageHistoryUseCase(repository)

        useCase.clear()

        assertTrue(useCase.entries().isEmpty())
    }
}
