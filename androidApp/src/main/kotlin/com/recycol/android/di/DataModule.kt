package com.recycol.android.di

import com.recycol.android.data.AndroidDatabaseDriverFactory
import com.recycol.android.data.AndroidProfileSource
import com.recycol.android.data.DataStoreKeyValueStore
import com.recycol.data.bins.SqlDelightBinAvailabilityRepository
import com.recycol.data.db.DatabaseDriverFactory
import com.recycol.data.db.createDatabase
import com.recycol.data.history.SqlDelightClassificationHistoryRepository
import com.recycol.data.profile.CatalogProfileSource
import com.recycol.data.profile.PersistentProfileRepository
import com.recycol.data.profile.ProfileCatalogSource
import com.recycol.data.storage.KeyValueStore
import com.recycol.domain.port.BinAvailabilityRepository
import com.recycol.domain.port.ClassificationHistoryRepository
import com.recycol.domain.port.ProfileRepository
import com.recycol.rules.profile.ProfileCatalog
import com.recycol.rules.profile.ProfileSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Módulo Koin de la capa de datos (S36, agente DATA).
 *
 * `ProfileRepository` reparte responsabilidades entre RULES (carga y
 * validación del catálogo) y DATA (persistencia de la selección) — issue
 * #48. Antes solo estaba el lado de DATA registrado en Koin; sin el lado de
 * RULES, resolver `ProfileRepository` fallaba en tiempo de ejecución (nunca
 * se llegó a probar en dispositivo real hasta ahora). El puente es
 * [CatalogProfileSource] sobre [ProfileCatalog], leyendo los perfiles
 * empaquetados con [AndroidProfileSource].
 */
val dataModule = module {
    single<KeyValueStore> { DataStoreKeyValueStore(androidContext()) }
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    single { createDatabase(get()) }
    single<BinAvailabilityRepository> { SqlDelightBinAvailabilityRepository(get()) }
    single<ClassificationHistoryRepository> { SqlDelightClassificationHistoryRepository(get()) }

    single<ProfileSource> { AndroidProfileSource(androidContext()) }
    single { ProfileCatalog(get()) }
    single<ProfileCatalogSource> { CatalogProfileSource(get()) }
    single<ProfileRepository> { PersistentProfileRepository(get(), get()) }
}
