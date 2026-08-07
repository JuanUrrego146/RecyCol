package com.botabien.android.inference

import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelProvider
import com.botabien.android.inference.roi.DetectorRoi
import com.botabien.android.inference.roi.GuideFrameRoi
import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature
import com.botabien.domain.port.DeviceTierPolicy
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertIs

class WasteClassifierFactoryTest {

    private class FakeModelProvider(private val available: Set<String>) : ModelProvider {
        override fun isAvailable(assetFileName: String) = assetFileName in available
        override fun load(assetFileName: String): ByteBuffer =
            if (isAvailable(assetFileName)) ByteBuffer.allocate(0) else throw IOException("no existe")
    }

    private class FakePolicy(
        override val tier: DeviceTier,
        private val enabled: Set<Feature> = emptySet(),
    ) : DeviceTierPolicy {
        override fun isEnabled(feature: Feature) = feature in enabled
    }

    @Test
    fun `sin modelos empaquetados se sirve el stub determinista`() {
        val classifier = WasteClassifierFactory.createForTier(
            provider = FakeModelProvider(available = emptySet()),
            policy = FakePolicy(DeviceTier.MID),
            tier = DeviceTier.MID,
        )

        assertIs<StubWasteClassifier>(classifier)
    }

    @Test
    fun `con el modelo de material presente se sirve el clasificador real`() {
        val classifier = WasteClassifierFactory.createForTier(
            provider = FakeModelProvider(available = setOf("material_mid.tflite")),
            policy = FakePolicy(DeviceTier.MID),
            tier = DeviceTier.MID,
        )

        // La construcción es perezosa: crear el clasificador real no toca LiteRT,
        // así que es seguro verificarlo en JVM sin el runtime nativo.
        assertIs<LiteRtWasteClassifier>(classifier)
    }

    @Test
    fun `con deteccion habilitada y modelo presente la estrategia es el detector`() {
        val strategy = WasteClassifierFactory.roiStrategyFor(
            provider = FakeModelProvider(available = setOf(ModelCatalog.DETECTOR.assetFileName)),
            policy = FakePolicy(DeviceTier.HIGH, enabled = setOf(Feature.OBJECT_DETECTION)),
        )

        assertIs<DetectorRoi>(strategy)
    }

    @Test
    fun `sin la funcion habilitada se usa el marco guia aunque el modelo exista`() {
        val strategy = WasteClassifierFactory.roiStrategyFor(
            provider = FakeModelProvider(available = setOf(ModelCatalog.DETECTOR.assetFileName)),
            policy = FakePolicy(DeviceTier.LOW),
        )

        assertIs<GuideFrameRoi>(strategy)
    }

    @Test
    fun `sin modelo de detector se usa el marco guia aunque la funcion este habilitada`() {
        val strategy = WasteClassifierFactory.roiStrategyFor(
            provider = FakeModelProvider(available = emptySet()),
            policy = FakePolicy(DeviceTier.HIGH, enabled = setOf(Feature.OBJECT_DETECTION)),
        )

        assertIs<GuideFrameRoi>(strategy)
    }

    @Test
    fun `la clasificacion por camara esta disponible en las tres gamas (RF-030)`() {
        val allModels = setOf(
            ModelCatalog.MATERIAL_LOW.assetFileName,
            ModelCatalog.MATERIAL_MID.assetFileName,
            ModelCatalog.MATERIAL_HIGH.assetFileName,
        )

        DeviceTier.entries.forEach { tier ->
            assertIs<LiteRtWasteClassifier>(
                WasteClassifierFactory.createForTier(
                    provider = FakeModelProvider(available = allModels),
                    policy = FakePolicy(tier),
                    tier = tier,
                ),
                "gama $tier con modelos empaquetados",
            )
            assertIs<StubWasteClassifier>(
                WasteClassifierFactory.createForTier(
                    provider = FakeModelProvider(available = emptySet()),
                    policy = FakePolicy(tier),
                    tier = tier,
                ),
                "gama $tier sin modelos: el stub mantiene la clasificación viva",
            )
        }
    }
}
