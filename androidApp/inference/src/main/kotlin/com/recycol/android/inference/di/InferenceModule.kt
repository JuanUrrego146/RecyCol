package com.recycol.android.inference.di

import com.recycol.android.inference.WasteClassifierFactory
import com.recycol.android.inference.tier.BenchmarkedTierPolicy
import com.recycol.android.inference.tier.TierPolicyFactory
import com.recycol.domain.port.DeviceTierPolicy
import com.recycol.domain.port.WasteClassifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Módulo Koin del runtime de inferencia.
 *
 * Expone los puertos [DeviceTierPolicy] y [WasteClassifier] del dominio; la
 * app los registra en su arranque de Koin (integración con `androidApp/di/`).
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
 */
val inferenceModule = module {
    single<DeviceTierPolicy> { TierPolicyFactory.create(androidContext()) }

    single<WasteClassifier> {
        WasteClassifierFactory.create(
            context = androidContext(),
            policy = get<DeviceTierPolicy>(),
        )
    }
}
