package com.botabien.android

import android.app.Application
import com.botabien.android.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Aplicación BotaBien: arranca la inyección de dependencias (S36).
 * Cada agente registra aquí su módulo Koin; el orden no importa porque la
 * resolución es perezosa.
 */
class BotaBienApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BotaBienApplication)
            modules(dataModule)
        }
    }
}
