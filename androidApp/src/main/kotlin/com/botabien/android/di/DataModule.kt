package com.botabien.android.di

import com.botabien.android.data.AndroidDatabaseDriverFactory
import com.botabien.android.data.DataStoreKeyValueStore
import com.botabien.data.bins.SqlDelightBinAvailabilityRepository
import com.botabien.data.db.DatabaseDriverFactory
import com.botabien.data.db.createDatabase
import com.botabien.data.history.SqlDelightClassificationHistoryRepository
import com.botabien.data.profile.PersistentProfileRepository
import com.botabien.data.storage.KeyValueStore
import com.botabien.domain.port.BinAvailabilityRepository
import com.botabien.domain.port.ClassificationHistoryRepository
import com.botabien.domain.port.ProfileRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Módulo Koin de la capa de datos (S36, agente DATA).
 *
 * `ProfileRepository` requiere un `ProfileCatalogSource` en el grafo: lo
 * registra el agente RULES (S30) al publicar la carga del catálogo de
 * perfiles (reparto acordado en la issue #48). Hasta entonces la resolución
 * es perezosa: registrar este módulo no falla, solo fallaría resolver
 * `ProfileRepository` sin catálogo.
 */
val dataModule = module {
    single<KeyValueStore> { DataStoreKeyValueStore(androidContext()) }
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    single { createDatabase(get()) }
    single<BinAvailabilityRepository> { SqlDelightBinAvailabilityRepository(get()) }
    single<ClassificationHistoryRepository> { SqlDelightClassificationHistoryRepository(get()) }
    single<ProfileRepository> { PersistentProfileRepository(get(), get()) }
}
