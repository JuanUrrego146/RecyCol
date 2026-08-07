package com.botabien.android.inference

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.botabien.android.inference.engine.AccelerationMode
import com.botabien.android.inference.engine.InferenceEngine
import com.botabien.android.inference.engine.LiteRtEngines
import com.botabien.android.inference.frame.BitmapImageFrame
import com.botabien.android.inference.image.FramePreprocessor
import com.botabien.android.inference.model.AssetModelProvider
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelOutputOrder
import com.botabien.android.inference.model.ModelSpec
import com.botabien.domain.model.WasteMaterial
import java.io.IOException
import kotlin.math.abs
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Banco de exactitud para los exports de ML (S27, #25): mide la pérdida que
 * introduce la cuantización INT8 y la exactitud top-1 sobre un conjunto de
 * evaluación real, contra el runtime real (mismo preprocesado que producción).
 *
 * Convenciones (documentadas en el README del módulo):
 * - Modelos en `assets/models/` del módulo (variantes INT8 del catálogo).
 *   El gemelo float de una variante se llama `<variante>_float.tflite`
 *   (entrada FLOAT32 RGB normalizada a `[0,1]`).
 * - Conjunto de evaluación en los assets del APK de prueba
 *   (`src/androidTest/assets/eval/`): `labels.csv` con líneas
 *   `archivo,MATERIAL` (nombres del enumerado `WasteMaterial`) e imágenes
 *   en la misma carpeta. **Debe ser un conjunto no visto en entrenamiento**
 *   (RealWaste como control, regla del proyecto: nunca medir sobre el
 *   dominio de entrenamiento).
 *
 * Reporta por variante: top-1 INT8, top-1 float, pérdida por cuantización,
 * acuerdo INT8↔float y delta medio de confianza (`REGISTRO-S27`). Sin
 * modelos o sin conjunto de evaluación, se omite (assume), no falla.
 */
@RunWith(AndroidJUnit4::class)
class QuantizationAccuracyBenchmark {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val provider = AssetModelProvider(targetContext)
    private val preprocessor = FramePreprocessor()

    private data class Sample(val fileName: String, val label: WasteMaterial)

    @Test
    fun perdidaDeExactitudPorCuantizacion() {
        val samples = loadEvalSet()
        assumeTrue("Sin conjunto de evaluación en androidTest/assets/eval: banco omitido", samples.isNotEmpty())

        val variants = listOf(ModelCatalog.MATERIAL_LOW, ModelCatalog.MATERIAL_MID, ModelCatalog.MATERIAL_HIGH)
            .filter { provider.isAvailable(it.assetFileName) }
        assumeTrue("Sin modelos empaquetados: banco omitido hasta S27", variants.isNotEmpty())

        variants.forEach { spec -> evaluateVariant(spec, samples) }
    }

    private fun evaluateVariant(int8Spec: ModelSpec, samples: List<Sample>) {
        val floatName = int8Spec.assetFileName.replace(".tflite", "_float.tflite")
        val floatSpec = int8Spec.copy(assetFileName = floatName, quantizedInput = false)
        val hasFloatTwin = provider.isAvailable(floatName)

        engineFor(int8Spec).use { int8Engine ->
            val floatEngine = if (hasFloatTwin) engineFor(floatSpec) else null
            try {
                var int8Hits = 0
                var floatHits = 0
                var agreements = 0
                var confidenceDelta = 0.0

                samples.forEach { sample ->
                    val frame = loadFrame(sample.fileName)
                    val int8 = predict(int8Engine, int8Spec, frame)
                    if (int8.first == sample.label) int8Hits++

                    if (floatEngine != null) {
                        val float = predict(floatEngine, floatSpec, frame)
                        if (float.first == sample.label) floatHits++
                        if (float.first == int8.first) agreements++
                        confidenceDelta += abs(float.second - int8.second)
                    }
                }

                val n = samples.size
                val top1Int8 = percent(int8Hits, n)
                if (floatEngine != null) {
                    val top1Float = percent(floatHits, n)
                    println(
                        "REGISTRO-S27 ${int8Spec.assetFileName}: n=$n top1Int8=$top1Int8% " +
                            "top1Float=$top1Float% perdidaCuantizacion=${top1Float - top1Int8}pp " +
                            "acuerdo=${percent(agreements, n)}% " +
                            "deltaConfianzaMedia=${"%.4f".format(confidenceDelta / n)}"
                    )
                } else {
                    println(
                        "REGISTRO-S27 ${int8Spec.assetFileName}: n=$n top1Int8=$top1Int8% " +
                            "(sin gemelo float $floatName: pérdida de cuantización no medible)"
                    )
                }
            } finally {
                floatEngine?.close()
            }
        }
    }

    private fun engineFor(spec: ModelSpec): InferenceEngine =
        LiteRtEngines.create(provider.load(spec.assetFileName), spec.assetFileName, AccelerationMode.CPU)

    private fun predict(
        engine: InferenceEngine,
        spec: ModelSpec,
        frame: BitmapImageFrame,
    ): Pair<WasteMaterial, Float> {
        val scores = Scores.toProbabilities(
            engine.run(preprocessor.preprocess(frame, spec)),
            spec.outputsProbabilities,
        )
        check(scores.size == ModelOutputOrder.MATERIALS.size) {
            "${spec.assetFileName}: ${scores.size} clases; la taxonomía declara ${ModelOutputOrder.MATERIALS.size}"
        }
        val winner = Scores.argmax(scores)
        return ModelOutputOrder.MATERIALS[winner] to scores[winner]
    }

    private fun loadFrame(fileName: String): BitmapImageFrame {
        val bitmap = testContext.assets.open("$EVAL_DIR/$fileName").use(BitmapFactory::decodeStream)
            ?: error("No se pudo decodificar $fileName")
        return BitmapImageFrame(bitmap, timestampMillis = 0L)
    }

    private fun loadEvalSet(): List<Sample> = try {
        testContext.assets.open("$EVAL_DIR/labels.csv").bufferedReader().useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val (file, label) = line.split(',', limit = 2).map(String::trim)
                    val material = WasteMaterial.entries.firstOrNull { it.name == label }
                        ?: error("labels.csv: etiqueta desconocida «$label» (usar nombres de WasteMaterial)")
                    Sample(file, material)
                }
                .toList()
        }
    } catch (_: IOException) {
        emptyList()
    }

    private fun percent(hits: Int, total: Int): Int =
        if (total == 0) 0 else (hits * 100) / total

    private companion object {
        const val EVAL_DIR = "eval"
    }
}
