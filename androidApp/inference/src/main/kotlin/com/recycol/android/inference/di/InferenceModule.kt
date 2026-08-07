package com.recycol.android.inference.di

import com.recycol.android.inference.WasteClassifierFactory
import com.recycol.android.inference.tier.BenchmarkedTierPolicy
import com.recycol.android.inference.tier.TierPolicyFactory
import com.recycol.domain.model.DeviceTier
import com.recycol.domain.port.DeviceTierPolicy
import com.recycol.domain.port.TierPreferenceRepository
import com.recycol.domain.port.WasteClassifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Adaptador de [TierPreferenceRepository] (dominio) sobre la instancia real
 * de [BenchmarkedTierPolicy] (coordinación #94).
 *
 * Delega en la misma instancia que expone `DeviceTierPolicy` — no crea una
 * segunda copia del estado — para que un cambio desde ajustes se refleje de
 * inmediato en la clasificación siguiente, sin reiniciar la app.
 */
private class BenchmarkedTierPreferenceRepository(
    private val policy: BenchmarkedTierPolicy,
) : TierPreferenceRepository {
    override suspend fun manualOverride(): DeviceTier? = policy.manualOverride()
    override suspend fun setManualOverride(tier: DeviceTier?) = policy.setManualOverride(tier)
}

/**
 * Módulo Koin del runtime de inferencia.
 *
 * Expone los puertos [DeviceTierPolicy], [TierPreferenceRepository] y
 * [WasteClassifier] del dominio; la app los registra en su arranque de Koin
 * (integración con `androidApp/di/`).
 *
 * La política arranca con la gama cacheada o, en el primer arranque, con la
 * postura conservadora (gama baja) sin bloquear. El arranque de la app debe
 * llamar una vez a [BenchmarkedTierPolicy.ensureResolved] fuera del hilo
 * principal para correr el micro-benchmark y fijar la gama real (RF-029);
 * hasta entonces todo funciona en modo conservador (invariante 5).
 *
 * El `WasteClassifier` expuesto es el adaptador consciente de gama
 * (coordinación #102): aunque sea un singleton y se inyecte antes de que
 * `ensureResolved` termine, cada clasificación consulta la política y
 * recambia modelo y ROI si la gama cambió (resolución tardía, degradación
 * en uso o ajuste manual). No hace falta re-crear el grafo de Koin.
 *
 * `DeviceTierPolicy` y `TierPreferenceRepository` comparten una única
 * instancia de [BenchmarkedTierPolicy] (registrada aparte y enlazada dos
 * veces): son dos puertos de dominio sobre el mismo estado, no dos estados.
 */
val inferenceModule = module {
    single { TierPolicyFactory.create(androidContext()) }
    single<DeviceTierPolicy> { get<BenchmarkedTierPolicy>() }
    single<TierPreferenceRepository> { BenchmarkedTierPreferenceRepository(get()) }

    single<WasteClassifier> {
        WasteClassifierFactory.create(
            context = androidContext(),
            policy = get<DeviceTierPolicy>(),
        )
    }
}
