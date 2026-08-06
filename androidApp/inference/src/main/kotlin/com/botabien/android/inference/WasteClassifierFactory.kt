package com.botabien.android.inference

import android.content.Context
import com.botabien.android.inference.engine.LiteRtEngines
import com.botabien.android.inference.engine.ResilientInferenceEngine
import com.botabien.android.inference.model.AssetModelProvider
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelProvider
import com.botabien.android.inference.model.ModelSpec
import com.botabien.domain.model.DeviceTier
import com.botabien.domain.port.WasteClassifier

/**
 * Construye el [WasteClassifier] de producción para la gama resuelta.
 *
 * Selecciona la variante de modelo de la gama (matriz de
 * `context-for-vibe-coding.md`), envuelve cada modelo en un motor con
 * respaldo NNAPI → GPU → CPU y decide entre el clasificador real y el stub
 * según haya o no modelos empaquetados. La carga del modelo y la creación del
 * intérprete son perezosas: ocurren en la primera inferencia.
 */
object WasteClassifierFactory {

    fun create(context: Context, tier: DeviceTier): WasteClassifier =
        create(AssetModelProvider(context.applicationContext), tier)

    /** Variante inyectable para pruebas: mismo cableado, proveedor arbitrario. */
    internal fun create(provider: ModelProvider, tier: DeviceTier): WasteClassifier {
        val materialSpec = ModelCatalog.materialSpecFor(tier)
        if (!provider.isAvailable(materialSpec)) {
            // Sin modelo de material no hay etapa 1 posible: la app sigue
            // funcionando contra el stub determinista hasta que ML publique (S27).
            return StubWasteClassifier()
        }

        val contaminationSpec = ModelCatalog.CONTAMINATION
        val contaminationEngine = if (provider.isAvailable(contaminationSpec)) {
            resilientEngine(provider, contaminationSpec)
        } else {
            null
        }

        return LiteRtWasteClassifier(
            materialEngine = resilientEngine(provider, materialSpec),
            materialSpec = materialSpec,
            contaminationEngine = contaminationEngine,
            contaminationSpec = contaminationSpec,
        )
    }

    private fun resilientEngine(provider: ModelProvider, spec: ModelSpec) =
        ResilientInferenceEngine(
            buildEngine = { mode -> LiteRtEngines.create(provider.load(spec), spec, mode) },
        )
}
