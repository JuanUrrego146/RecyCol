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
import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature
import com.botabien.domain.port.DeviceTierPolicy
import com.botabien.domain.port.WasteClassifier

/**
 * Construye el [WasteClassifier] de producción.
 *
 * Lo que se expone es un [TierAwareWasteClassifier] (coordinación #102): la
 * política de gama se consulta en cada clasificación y el clasificador
 * concreto —variante de modelo según la matriz del proyecto, motor con
 * respaldo NNAPI → GPU → CPU, estrategia de ROI— se reconstruye cuando la
 * gama cambia (resolución tardía del benchmark, degradación en uso o ajuste
 * manual). Sin modelos empaquetados se sirve el stub determinista (S27 trae
 * los reales). La carga del modelo y la creación del intérprete son
 * perezosas: ocurren en la primera inferencia.
 */
object WasteClassifierFactory {

    fun create(context: Context, policy: DeviceTierPolicy): WasteClassifier {
        val provider = AssetModelProvider(context.applicationContext)
        return TierAwareWasteClassifier(policy) { tier ->
            createForTier(provider, policy, tier)
        }
    }

    /** Clasificador concreto para una gama; lo recambia el adaptador (#102). */
    internal fun createForTier(
        provider: ModelProvider,
        policy: DeviceTierPolicy,
        tier: DeviceTier,
    ): WasteClassifier {
        val materialSpec = ModelCatalog.materialSpecFor(tier)
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
            // Señal extremo a extremo (ROI + preprocesado + inferencia) para la
            // degradación de gama en uso (coordinación #103).
            onClassifyLatencyMillis = (policy as? BenchmarkedTierPolicy)
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
