package com.recycol.data.history

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.recycol.data.db.RecyColDatabase
import com.recycol.data.db.DatabaseDriverFactory
import com.recycol.data.db.createDatabase
import com.recycol.domain.model.BinId
import com.recycol.domain.model.ClassificationRecord
import com.recycol.domain.model.WasteMaterial
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato del historial local (S37; RF-032 a RF-034, RNF-012, RNF-014):
 * registra solo resultados, consulta de más reciente a más antiguo, borrado
 * efectivo, supervivencia al cierre y ninguna imagen escrita a disco.
 */
class ClassificationHistoryPersistenceTest {

    private val directory: File = Files.createTempDirectory("recycol-s37").toFile()
    private val databaseFile = File(directory, "recycol.db")
    private var openDriver: SqlDriver? = null

    @AfterTest
    fun cleanUp() {
        openDriver?.close()
        directory.deleteRecursively()
    }

    @Test
    fun elHistorialSeConsultaDeMasRecienteAMasAntiguo() = runTest {
        val repository = SqlDelightClassificationHistoryRepository(openDatabase())
        repository.record(recordOf(id = "r1", timestampMillis = 1_000L))
        repository.record(recordOf(id = "r3", timestampMillis = 3_000L))
        repository.record(recordOf(id = "r2", timestampMillis = 2_000L))

        assertEquals(listOf("r3", "r2", "r1"), repository.history().map { it.id })
    }

    @Test
    fun cadaEntradaConservaMaterialCanecaYFecha() = runTest {
        val repository = SqlDelightClassificationHistoryRepository(openDatabase())
        val record = ClassificationRecord(
            id = "r1",
            material = WasteMaterial.BEVERAGE_CARTON,
            binId = BinId("black"),
            timestampMillis = 1_722_000_000_000L,
        )

        repository.record(record)

        assertEquals(listOf(record), repository.history())
    }

    @Test
    fun elHistorialSobreviveAlCierreYReapertura() = runTest {
        val firstSession = SqlDelightClassificationHistoryRepository(openDatabase())
        firstSession.record(recordOf(id = "r1", timestampMillis = 1_000L))
        closeDatabase()

        val secondSession = SqlDelightClassificationHistoryRepository(openDatabase())

        assertEquals(listOf("r1"), secondSession.history().map { it.id })
    }

    @Test
    fun elBorradoEsEfectivoYTambienSobreviveAlCierre() = runTest {
        val firstSession = SqlDelightClassificationHistoryRepository(openDatabase())
        firstSession.record(recordOf(id = "r1", timestampMillis = 1_000L))
        firstSession.record(recordOf(id = "r2", timestampMillis = 2_000L))

        firstSession.clear()
        closeDatabase()

        val secondSession = SqlDelightClassificationHistoryRepository(openDatabase())
        assertEquals(emptyList(), secondSession.history())
    }

    /**
     * RNF-012: el esquema del historial no admite columnas binarias, así que
     * ningún frame de cámara puede persistirse por esta vía.
     */
    @Test
    fun elEsquemaDelHistorialNoAdmiteDatosBinarios() = runTest {
        openDatabase()
        val ddl = tableDefinitions()

        assertTrue(ddl.isNotEmpty(), "sqlite_master debe declarar las tablas del esquema")
        ddl.forEach { definition ->
            assertFalse(
                definition.uppercase().contains("BLOB"),
                "El esquema no debe declarar columnas binarias: $definition",
            )
        }
    }

    /**
     * RNF-012, verificación física: tras un uso completo del historial, el
     * archivo de la base de datos no contiene firmas de imagen JPEG ni PNG.
     */
    @Test
    fun elArchivoDeLaBaseDeDatosNoContieneImagenes() = runTest {
        val repository = SqlDelightClassificationHistoryRepository(openDatabase())
        repository.record(recordOf(id = "r1", timestampMillis = 1_000L))
        repository.record(recordOf(id = "r2", timestampMillis = 2_000L))
        closeDatabase()

        val bytes = databaseFile.readBytes()
        val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        assertFalse(bytes.containsSequence(jpegSignature), "El archivo contiene una firma JPEG")
        assertFalse(bytes.containsSequence(pngSignature), "El archivo contiene una firma PNG")
    }

    private fun recordOf(id: String, timestampMillis: Long) = ClassificationRecord(
        id = id,
        material = WasteMaterial.PLASTIC,
        binId = BinId("white"),
        timestampMillis = timestampMillis,
    )

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

    private fun tableDefinitions(): List<String> {
        val driver = checkNotNull(openDriver)
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT sql FROM sqlite_master WHERE type = 'table' AND sql IS NOT NULL",
            mapper = { cursor ->
                val definitions = mutableListOf<String>()
                while (cursor.next().value) {
                    cursor.getString(0)?.let(definitions::add)
                }
                QueryResult.Value(definitions.toList())
            },
            parameters = 0,
        ).value
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || size < needle.size) return false
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) {
                if (this[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }
}
