package com.recycol.android.inference.image

import com.recycol.android.inference.FakePixelFrame
import com.recycol.android.inference.model.ModelCatalog
import com.recycol.android.inference.roi.CropRegion
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Registro de la parte JVM-medible del presupuesto de latencia (S20): el
 * preprocesado. La inferencia y el total extremo a extremo se miden en
 * dispositivo con [com.recycol.android.inference.DeviceLatencyBenchmark].
 *
 * Imprime las cifras con el prefijo `REGISTRO-S20` (quedan en el reporte de
 * pruebas). La aserción es una cota de cordura holgada, no el objetivo de
 * RNF-001: los runners de CI varían y este número solo detecta regresiones
 * de orden de magnitud (p. ej. volver a recorrer el frame completo).
 */
class PreprocessorThroughputBenchmarkTest {

    @Test
    fun `preprocesado de un frame 1080p dentro de una cota de cordura`() {
        val preprocessor = FramePreprocessor()
        // Frame sintético con gradiente para que el muestreo no sea trivial.
        val width = 1920
        val height = 1080
        val pixels = IntArray(width * height) { index ->
            val v = index % 256
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val frame = FakePixelFrame(width, height, pixels)
        val spec = ModelCatalog.MATERIAL_LOW
        val region = CropRegion.centeredFraction(width, height, 0.8f)

        repeat(WARMUP_RUNS) { preprocessor.preprocess(frame, spec, region) }

        val samples = LongArray(MEASURED_RUNS) {
            val start = System.nanoTime()
            preprocessor.preprocess(frame, spec, region)
            (System.nanoTime() - start) / NANOS_PER_MILLI
        }
        samples.sort()
        val median = samples[samples.size / 2]

        println(
            "REGISTRO-S20 preprocesado JVM: frame=${width}x$height region=${region.size} " +
                "destino=${spec.inputSize} medianaMs=$median " +
                "muestras=${samples.joinToString(",")}"
        )

        assertTrue(
            median < SANITY_CEILING_MILLIS,
            "El preprocesado tardó ${median}ms de mediana: regresión de orden de magnitud",
        )
    }

    private companion object {
        const val WARMUP_RUNS = 5
        const val MEASURED_RUNS = 15
        const val NANOS_PER_MILLI = 1_000_000L
        const val SANITY_CEILING_MILLIS = 250L
    }
}
