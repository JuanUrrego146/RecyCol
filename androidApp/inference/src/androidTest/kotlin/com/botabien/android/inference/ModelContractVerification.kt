package com.botabien.android.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.botabien.android.inference.engine.AccelerationMode
import com.botabien.android.inference.engine.LiteRtEngines
import com.botabien.android.inference.model.AssetModelProvider
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelOutputOrder
import com.botabien.android.inference.model.ModelSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verificación del contrato de modelos para los exports de ML (S27, #25).
 *
 * Para cada `.tflite` presente en `assets/models/` comprueba contra el
 * runtime real lo que el contrato del README exige: forma y tipo del tensor
 * de entrada, número de clases de salida, y que una inferencia de humo
 * produce probabilidades (suma ≈ 1 tras decuantizar). Un export que rompa
 * el contrato falla aquí con mensaje explícito ANTES de llegar a la app.
 *
 * Sin modelos empaquetados las pruebas se omiten (assume), no fallan.
 * Resultados con prefijo `REGISTRO-S27` en el log de instrumentación.
 */
@RunWith(AndroidJUnit4::class)
class ModelContractVerification {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val provider = AssetModelProvider(context)

    @Test
    fun losModelosEmpaquetadosCumplenElContrato() {
        val specs = listOf(
            ModelCatalog.MATERIAL_LOW to ModelOutputOrder.MATERIALS.size,
            ModelCatalog.MATERIAL_MID to ModelOutputOrder.MATERIALS.size,
            ModelCatalog.MATERIAL_HIGH to ModelOutputOrder.MATERIALS.size,
            ModelCatalog.CONTAMINATION to ModelOutputOrder.CONTAMINATION.size,
        )
        val present = specs.filter { (spec, _) -> provider.isAvailable(spec.assetFileName) }
        assumeTrue("Sin modelos empaquetados: verificación omitida hasta S27", present.isNotEmpty())

        present.forEach { (spec, expectedClasses) -> verifyClassifier(spec, expectedClasses) }
    }

    private fun verifyClassifier(spec: ModelSpec, expectedClasses: Int) {
        LiteRtEngines.create(provider.load(spec.assetFileName), spec.assetFileName, AccelerationMode.CPU)
            .use { engine ->
                val scores = engine.run(grayInput(spec))

                assertEquals(
                    "${spec.assetFileName}: clases de salida distintas del contrato " +
                        "(¿label_mapping desincronizado con WasteMaterial?)",
                    expectedClasses,
                    scores.size,
                )
                val sum = scores.sum()
                assertTrue(
                    "${spec.assetFileName}: la salida no parece softmax (suma=$sum); " +
                        "el contrato exige probabilidades en la última capa",
                    sum in 0.9f..1.1f,
                )
                assertTrue(
                    "${spec.assetFileName}: hay puntuaciones fuera de [0,1]",
                    scores.all { it in -0.01f..1.01f },
                )
                println(
                    "REGISTRO-S27 contrato OK: ${spec.assetFileName} " +
                        "clases=${scores.size} sumaSoftmax=$sum"
                )
            }
    }

    /** Entrada sintética gris conforme a la spec (el runtime valida forma y tipo al ejecutar). */
    private fun grayInput(spec: ModelSpec): ByteBuffer {
        val channels = 3
        val bytesPerChannel = if (spec.quantizedInput) 1 else 4
        val buffer = ByteBuffer
            .allocateDirect(spec.inputSize * spec.inputSize * channels * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        while (buffer.hasRemaining()) {
            if (spec.quantizedInput) buffer.put(0x80.toByte()) else buffer.putFloat(0.5f)
        }
        buffer.rewind()
        return buffer
    }
}
