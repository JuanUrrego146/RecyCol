package com.recycol.android

import android.app.Application
import com.recycol.android.di.authModule
import com.recycol.android.di.dataModule
import com.recycol.android.inference.di.inferenceModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Aplicación RecyCol: arranca la inyección de dependencias (S36).
 * Cada agente registra aquí su módulo Koin; el orden no importa porque la
 * resolución es perezosa.
 *
 * `inferenceModule` (EDGE) quedó escrito y probado en aislamiento desde S18
 * pero nunca se había registrado aquí: la app corría entera sobre fakes de
 * `shared/testing/` hasta el cierre de v1 (cierre de M4, cableado real).
 */
class RecyColApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RecyColApplication)
            modules(dataModule, authModule, inferenceModule)
        }
    }
}
