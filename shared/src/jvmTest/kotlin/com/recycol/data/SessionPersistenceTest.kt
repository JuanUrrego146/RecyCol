package com.recycol.data

import com.recycol.data.bins.SqlDelightBinAvailabilityRepository
import com.recycol.data.db.RecyColDatabase
import com.recycol.data.db.DatabaseDriverFactory
import com.recycol.data.db.createDatabase
import com.recycol.data.profile.PersistentProfileRepository
import com.recycol.data.storage.PropertiesKeyValueStore
import com.recycol.domain.model.BinDefinition
import com.recycol.domain.model.BinId
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.DisposalRoute
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Criterio de hecho de S36 (RNF-014): país y canecas disponibles se recuperan
 * tras cerrar y reabrir la «aplicación». Cada sesión se simula con instancias
 * nuevas de driver y almacén sobre los mismos archivos.
 */
class SessionPersistenceTest {

    private val directory: File = Files.createTempDirectory("recycol-s36").toFile()
    private val databaseFile = File(directory, "recycol.db")
    private val preferencesFile = File(directory, "preferences.properties")
    private var openDriver: SqlDriver? = null

    @AfterTest
    fun cleanUp() {
        openDriver?.close()
        directory.deleteRecursively()
    }

    @Test
    fun lasCanecasDisponiblesSobrevivenAlCierreYReapertura() = runTest {
        val firstSession = SqlDelightBinAvailabilityRepository(openDatabase())
        firstSession.saveAvailableBins(setOf(BinId("white"), BinId("black")))
        closeDatabase()

        val secondSession = SqlDelightBinAvailabilityRepository(openDatabase())

        assertEquals(setOf(BinId("white"), BinId("black")), secondSession.availableBins())
    }

    @Test
    fun guardarUnaSeleccionDeCanecasReemplazaLaAnterior() = runTest {
        val repository = SqlDelightBinAvailabilityRepository(openDatabase())
        repository.saveAvailableBins(setOf(BinId("white"), BinId("green")))

        repository.saveAvailableBins(setOf(BinId("black")))

        assertEquals(setOf(BinId("black")), repository.availableBins())
    }

    @Test
    fun unaSeleccionVaciaSignificaSinRestriccionYTambienPersiste() = runTest {
        val firstSession = SqlDelightBinAvailabilityRepository(openDatabase())
        firstSession.saveAvailableBins(setOf(BinId("white")))
        firstSession.saveAvailableBins(emptySet())
        closeDatabase()

        val secondSession = SqlDelightBinAvailabilityRepository(openDatabase())

        assertEquals(emptySet(), secondSession.availableBins())
    }

    @Test
    fun elPaisActivoSobreviveAlCierreYReapertura() = runTest {
        val firstSession = profileRepository()
        firstSession.setActiveProfile("co")

        val secondSession = profileRepository()

        assertEquals("co", secondSession.activeProfileOrNull()?.isoCode)
    }

    private fun openDatabase(): RecyColDatabase {
        val factory = DatabaseDriverFactory {
            val fresh = !databaseFile.exists()
            JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}").also { driver ->
                if (fresh) RecyColDatabase.Schema.create(driver)
                openDriver = driver
            }
        }
        return createDatabase(factory)
    }

    private fun closeDatabase() {
        openDriver?.close()
        openDriver = null
    }

    private fun profileRepository(): PersistentProfileRepository {
        val bin = BinDefinition(
            id = BinId("black"),
            displayName = "Caneca negra",
            colorHex = "#000000",
            route = DisposalRoute.NON_RECYCLABLE,
        )
        val colombia = CountryProfile(
            isoCode = "co",
            regulationName = "Norma de prueba",
            regulationReference = "Perfil sintético para pruebas — no citable",
            bins = listOf(bin),
            rules = emptyList(),
            inspectionRules = emptyList(),
            conservativeBin = bin.id,
        )
        return PersistentProfileRepository(
            catalogSource = { listOf(colombia) },
            store = PropertiesKeyValueStore(preferencesFile),
        )
    }
}
