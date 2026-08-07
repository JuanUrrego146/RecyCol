package com.recycol.android

import android.app.Application
import com.recycol.android.di.authModule
import com.recycol.android.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Aplicación RecyCol: arranca la inyección de dependencias (S36).
 * Cada agente registra aquí su módulo Koin; el orden no importa porque la
 * resolución es perezosa.
 */
class RecyColApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RecyColApplication)
            modules(dataModule, authModule)
        }
    }
}
