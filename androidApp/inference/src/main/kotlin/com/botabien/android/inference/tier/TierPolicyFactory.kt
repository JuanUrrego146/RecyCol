package com.botabien.android.inference.tier

import android.content.Context
import com.botabien.android.inference.engine.LiteRtEngines
import com.botabien.android.inference.engine.ResilientInferenceEngine
import com.botabien.android.inference.model.AssetModelProvider
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelProvider
import com.botabien.domain.model.DeviceTier

/**
 * Construye la [BenchmarkedTierPolicy] de producción (CUS-008).
 *
 * El resolutor combina el sondeo de capacidades con el micro-benchmark sobre
 * el modelo de gama baja (el más pequeño: si ni ese corre rápido, la gama es
 * baja con seguridad). Sin modelos empaquetados el benchmark devuelve nulo y
 * deciden las capacidades solas.
 */
object TierPolicyFactory {

    fun create(context: Context): BenchmarkedTierPolicy {
        val appContext = context.applicationContext
        return create(context, PrefsTierStore(appContext))
    }

    /**
     * Variante con almacén compartido: la DI reutiliza el mismo [TierStore]
     * para la política y para el puerto `TierPreferenceRepository` (#94).
     */
    fun create(context: Context, store: TierStore): BenchmarkedTierPolicy {
        val appContext = context.applicationContext
        return create(
            probe = AndroidCapabilitiesProbe(appContext),
            store = store,
            provider = AssetModelProvider(appContext),
        )
    }

    /** Variante inyectable para pruebas: mismo cableado, dobles arbitrarios. */
    internal fun create(
        probe: CapabilitiesProbe,
        store: TierStore,
        provider: ModelProvider,
    ): BenchmarkedTierPolicy = BenchmarkedTierPolicy(
        store = store,
        resolveTier = { resolve(probe, provider) },
    )

    private fun resolve(probe: CapabilitiesProbe, provider: ModelProvider): DeviceTier {
        val capabilities = probe.probe()
        val median = benchmarkMedian(provider)
        return TierResolver.resolve(capabilities, median)
    }

    private fun benchmarkMedian(provider: ModelProvider): Long? {
        val spec = ModelCatalog.MATERIAL_LOW
        if (!provider.isAvailable(spec.assetFileName)) return null
        val engine = ResilientInferenceEngine(
            buildEngine = { mode ->
                LiteRtEngines.create(provider.load(spec.assetFileName), spec.assetFileName, mode)
            },
        )
        return engine.use { WarmupBenchmark(spec, it).medianLatencyMillis() }
    }
}
