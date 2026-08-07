package com.recycol.android.di

import com.recycol.android.data.AndroidDatabaseDriverFactory
import com.recycol.android.data.DataStoreKeyValueStore
import com.recycol.data.bins.SqlDelightBinAvailabilityRepository
import com.recycol.data.db.DatabaseDriverFactory
import com.recycol.data.db.createDatabase
import com.recycol.data.history.SqlDelightClassificationHistoryRepository
import com.recycol.data.profile.PersistentProfileRepository
import com.recycol.data.storage.KeyValueStore
import com.recycol.domain.port.BinAvailabilityRepository
import com.recycol.domain.port.ClassificationHistoryRepository
import com.recycol.domain.port.ProfileRepository
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
