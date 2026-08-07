package com.botabien.android.inference

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.botabien.android.inference.engine.AccelerationMode
import com.botabien.android.inference.engine.LiteRtEngines
import com.botabien.android.inference.frame.BitmapImageFrame
import com.botabien.android.inference.image.FramePreprocessor
import com.botabien.android.inference.model.AssetModelProvider
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelOutputOrder
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verificación del lote de referencia de ML (S27, #25, punto 2 de su pedido):
 * confirma que el orden de clases del export coincide índice a índice con
 * [ModelOutputOrder] — es decir, que `label_mapping.yaml` y `WasteMaterial`
 * no se desincronizaron.
 *
 * Formato acordado (`src/androidTest/assets/eval/reference/`):
 * - `reference.csv`: líneas `archivo,indiceEsperado` donde el índice es el
 *   argmax que ML midió con SU pipeline sobre el MISMO artefacto INT8.
 *   Idealmente cubre las 11 clases al menos una vez.
 * - Las imágenes referenciadas, en la misma carpeta.
 *
 * Un desfase de orden aparece aquí como discrepancia sistemática de índices,
 * independiente de la exactitud del modelo. Pequeñas divergencias por el
 * preprocesado (bilineal vs. el de ML) pueden mover casos limítrofes: el
 * umbral de acuerdo exigido es del 90 %, y cada discrepancia se registra con
 * ambos índices y materiales para diagnóstico.
 */
@RunWith(AndroidJUnit4::class)
class ReferenceBatchVerification {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val provider = AssetModelProvider(targetContext)
    private val preprocessor = FramePreprocessor()

    @Test
    fun elOrdenDeClasesDelExportCoincideConElRuntime() {
        val references = loadReferences()
        assumeTrue("Sin lote de referencia en eval/reference: verificación omitida", references.isNotEmpty())

        val spec = listOf(ModelCatalog.MATERIAL_LOW, ModelCatalog.MATERIAL_MID, ModelCatalog.MATERIAL_HIGH)
            .firstOrNull { provider.isAvailable(it.assetFileName) }
        assumeTrue("Sin modelo de material empaquetado: verificación omitida", spec != null)
        checkNotNull(spec)

        LiteRtEngines.create(provider.load(spec.assetFileName), spec.assetFileName, AccelerationMode.CPU)
            .use { engine ->
                var agreements = 0
                val mismatches = mutableListOf<String>()

                references.forEach { (fileName, expectedIndex) ->
                    val bitmap = testContext.assets.open("$REFERENCE_DIR/$fileName")
                        .use(BitmapFactory::decodeStream)
                        ?: error("No se pudo decodificar $fileName")
                    val scores = Scores.toProbabilities(
                        engine.run(preprocessor.preprocess(BitmapImageFrame(bitmap, 0L), spec)),
                        spec.outputsProbabilities,
                    )
                    val actualIndex = Scores.argmax(scores)
                    if (actualIndex == expectedIndex) {
                        agreements++
                    } else {
                        mismatches += "$fileName: esperado $expectedIndex " +
                            "(${ModelOutputOrder.MATERIALS.getOrNull(expectedIndex)}) " +
                            "vs runtime $actualIndex (${ModelOutputOrder.MATERIALS.getOrNull(actualIndex)})"
                    }
                }

                val agreementPercent = agreements * 100 / references.size
                println(
                    "REGISTRO-S27 lote de referencia (${spec.assetFileName}): " +
                        "n=${references.size} acuerdo=$agreementPercent%"
                )
                mismatches.forEach { println("REGISTRO-S27 discrepancia: $it") }

                assertTrue(
                    "Acuerdo del $agreementPercent% con el lote de referencia (umbral 90%): " +
                        "posible desorden de clases entre label_mapping.yaml y WasteMaterial. " +
                        "Discrepancias:\n${mismatches.joinToString("\n")}",
                    agreementPercent >= MIN_AGREEMENT_PERCENT,
                )
            }
    }

    private fun loadReferences(): List<Pair<String, Int>> = try {
        testContext.assets.open("$REFERENCE_DIR/reference.csv").bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val (file, index) = line.split(',', limit = 2).map(String::trim)
                    file to index.toInt()
                }
                .toList()
        }
    } catch (_: IOException) {
        emptyList()
    }

    private companion object {
        const val REFERENCE_DIR = "eval/reference"
        const val MIN_AGREEMENT_PERCENT = 90
    }
}
