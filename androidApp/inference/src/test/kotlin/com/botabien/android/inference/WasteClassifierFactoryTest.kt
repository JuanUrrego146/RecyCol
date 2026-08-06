package com.botabien.android.inference

import com.botabien.android.inference.model.ModelProvider
import com.botabien.android.inference.model.ModelSpec
import com.botabien.domain.model.DeviceTier
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertIs

class WasteClassifierFactoryTest {

    private class FakeModelProvider(private val available: Set<String>) : ModelProvider {
        override fun isAvailable(spec: ModelSpec) = spec.assetFileName in available
        override fun load(spec: ModelSpec): ByteBuffer =
            if (isAvailable(spec)) ByteBuffer.allocate(0) else throw IOException("no existe")
    }

    @Test
    fun `sin modelos empaquetados la fabrica sirve el stub determinista`() {
        val classifier = WasteClassifierFactory.create(
            provider = FakeModelProvider(available = emptySet()),
            tier = DeviceTier.MID,
        )

        assertIs<StubWasteClassifier>(classifier)
    }

    @Test
    fun `con el modelo de material presente la fabrica sirve el clasificador real`() {
        val classifier = WasteClassifierFactory.create(
            provider = FakeModelProvider(available = setOf("material_mid.tflite")),
            tier = DeviceTier.MID,
        )

        // La construcción es perezosa: crear el clasificador real no toca LiteRT,
        // así que es seguro verificarlo en JVM sin el runtime nativo.
        assertIs<LiteRtWasteClassifier>(classifier)
    }
}
