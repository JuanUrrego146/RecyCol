package com.botabien.android.inference.di

import com.botabien.android.inference.WasteClassifierFactory
import com.botabien.android.inference.tier.BenchmarkedTierPolicy
import com.botabien.android.inference.tier.TierPolicyFactory
import com.botabien.domain.port.DeviceTierPolicy
import com.botabien.domain.port.WasteClassifier
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
