package com.botabien.android.inference

import android.content.Context
import com.botabien.android.inference.engine.LiteRtEngines
import com.botabien.android.inference.engine.ResilientInferenceEngine
import com.botabien.android.inference.model.AssetModelProvider
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelProvider
import com.botabien.android.inference.roi.DetectorRoi
import com.botabien.android.inference.roi.GuideFrameRoi
import com.botabien.android.inference.roi.RoiStrategy
import com.botabien.android.inference.tier.BenchmarkedTierPolicy
import com.botabien.domain.model.Feature
import com.botabien.domain.port.DeviceTierPolicy
import com.botabien.domain.port.WasteClassifier

/**
 * Construye el [WasteClassifier] de producción para la gama resuelta.
 *
 * Selecciona la variante de modelo de la gama (matriz de
 * `context-for-vibe-coding.md`), envuelve cada modelo en un motor con
 * respaldo NNAPI → GPU → CPU y decide entre el clasificador real y el stub
 * según haya o no modelos empaquetados. La estrategia de aislamiento del
 * objeto (RF-010) se elige consultando la política de gama (invariante 5):
 * detector si `OBJECT_DETECTION` está habilitada y el modelo existe; marco
 * guía fijo en cualquier otro caso. La carga del modelo y la creación del
 * intérprete son perezosas: ocurren en la primera inferencia.
 */
object WasteClassifierFactory {

    fun create(context: Context, policy: DeviceTierPolicy): WasteClassifier =
        create(AssetModelProvider(context.applicationContext), policy)

    /** Variante inyectable para pruebas: mismo cableado, proveedor arbitrario. */
    internal fun create(provider: ModelProvider, policy: DeviceTierPolicy): WasteClassifier {
        val materialSpec = ModelCatalog.materialSpecFor(policy.tier)
        if (!provider.isAvailable(materialSpec.assetFileName)) {
            // Sin modelo de material no hay etapa 1 posible: la app sigue
            // funcionando contra el stub determinista hasta que ML publique (S27).
            return StubWasteClassifier()
        }

        val contaminationSpec = ModelCatalog.CONTAMINATION
        val contaminationEngine = if (provider.isAvailable(contaminationSpec.assetFileName)) {
            resilientEngine(provider, contaminationSpec.assetFileName)
        } else {
            null
        }

        return LiteRtWasteClassifier(
            materialEngine = resilientEngine(provider, materialSpec.assetFileName),
            materialSpec = materialSpec,
            contaminationEngine = contaminationEngine,
            contaminationSpec = contaminationSpec,
            roiStrategy = roiStrategyFor(provider, policy),
            // Cierra el lazo de degradación en uso (S17/S20): la latencia
            // observada de la etapa de material alimenta a la política.
            onMaterialLatencyMillis = (policy as? BenchmarkedTierPolicy)
                ?.let { benchmarked -> benchmarked::reportObservedLatencyMillis },
        )
    }

    /**
     * Estrategia de ROI según la política de gama (RF-010). El detector es
     * una mejora opcional: sin la función habilitada o sin modelo empaquetado,
     * el marco guía fijo mantiene la clasificación funcionando en gama baja
     * y en cualquier degradación.
     */
    internal fun roiStrategyFor(provider: ModelProvider, policy: DeviceTierPolicy): RoiStrategy {
        val detectorSpec = ModelCatalog.DETECTOR
        val detectorEnabled = policy.isEnabled(Feature.OBJECT_DETECTION) &&
            provider.isAvailable(detectorSpec.assetFileName)
        return if (detectorEnabled) {
            DetectorRoi(
                engine = resilientEngine(provider, detectorSpec.assetFileName),
                spec = detectorSpec,
            )
        } else {
            GuideFrameRoi()
        }
    }

    private fun resilientEngine(provider: ModelProvider, assetFileName: String) =
        ResilientInferenceEngine(
            buildEngine = { mode ->
                LiteRtEngines.create(provider.load(assetFileName), assetFileName, mode)
            },
        )
}
