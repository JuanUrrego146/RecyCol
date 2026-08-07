package com.botabien.android.inference

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.botabien.android.inference.engine.LiteRtEngines
import com.botabien.android.inference.engine.ResilientInferenceEngine
import com.botabien.android.inference.model.AssetModelProvider
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.tier.WarmupBenchmark
import com.botabien.domain.model.DeviceTier
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Banco de latencia y memoria en dispositivo real (S20, RNF-001, RNF-007).
 *
 * Se ejecuta con `./gradlew :androidApp:inference:connectedDebugAndroidTest`
 * en un dispositivo de cada gama, con los modelos empaquetados en
 * `assets/models/`. Sin modelos, las pruebas se omiten (assume), no fallan:
 * los `.tflite` no se versionan y llegan con S27 (agente ML).
 *
 * Los resultados salen por el log de instrumentación con el prefijo
 * `REGISTRO-S20` para volcarlos a la tabla del README y al banco de S41.
 * Presupuesto de memoria del criterio de hecho: < 350 MB en clasificación.
 */
@RunWith(AndroidJUnit4::class)
class DeviceLatencyBenchmark {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val provider = AssetModelProvider(context)

    @Test
    fun latenciaYMemoriaPorVarianteDeModelo() {
        val anyModel = DeviceTier.entries.any {
            provider.isAvailable(ModelCatalog.materialSpecFor(it).assetFileName)
        }
        assumeTrue("Sin modelos empaquetados: banco omitido hasta S27", anyModel)

        DeviceTier.entries.forEach { tier ->
            val spec = ModelCatalog.materialSpecFor(tier)
            if (!provider.isAvailable(spec.assetFileName)) {
                log("variante ${spec.assetFileName} ausente: omitida")
                return@forEach
            }

            ResilientInferenceEngine(buildEngine = { mode ->
                LiteRtEngines.create(provider.load(spec.assetFileName), spec.assetFileName, mode)
            }).use { engine ->
                val median = WarmupBenchmark(spec, engine, maxRuns = 20, budgetMillis = 10_000)
                    .medianLatencyMillis()
                val usedMemoryMb = usedMemoryMb()

                log(
                    "variante=${spec.assetFileName} via=${engine.accelerationMode} " +
                        "latenciaMedianaMs=$median memoriaUsadaMb=$usedMemoryMb"
                )

                assertTrue(
                    "Memoria en clasificación ($usedMemoryMb MB) supera el presupuesto de 350 MB",
                    usedMemoryMb < MEMORY_BUDGET_MB,
                )
            }
        }
    }

    /** Heap JVM usado + heap nativo (donde viven intérprete y delegados), en MB. */
    private fun usedMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        val jvmUsed = runtime.totalMemory() - runtime.freeMemory()
        val nativeUsed = Debug.getNativeHeapAllocatedSize()
        return (jvmUsed + nativeUsed) / BYTES_PER_MB
    }

    private fun log(message: String) {
        // El log de instrumentación es la vía de registro del banco; no hay
        // frames aquí, solo cifras (RNF-012).
        println("REGISTRO-S20 $message")
    }

    private companion object {
        const val MEMORY_BUDGET_MB = 350L
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
