package com.botabien.android.inference.di

import com.botabien.android.inference.WasteClassifierFactory
import com.botabien.domain.model.DeviceTier
import com.botabien.domain.port.DeviceTierPolicy
import com.botabien.domain.port.WasteClassifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Módulo Koin del runtime de inferencia.
 *
 * Expone el puerto [WasteClassifier] del dominio; la app lo registra en su
 * arranque de Koin (integración con `androidApp/di/`). Mientras la política
 * de gama real no exista (S17), se asume la gama baja: la variante más
 * conservadora funciona en cualquier dispositivo (invariante 5). Cuando S17
 * registre [DeviceTierPolicy], este módulo la consume sin cambios.
 */
val inferenceModule = module {
    single<WasteClassifier> {
        WasteClassifierFactory.create(
            context = androidContext(),
            tier = getOrNull<DeviceTierPolicy>()?.tier ?: DeviceTier.LOW,
        )
    }
}
