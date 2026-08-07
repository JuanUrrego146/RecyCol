package com.recycol.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.recycol.data.storage.KeyValueStore
import kotlinx.coroutines.flow.first

/** Almacén de preferencias de la aplicación, único por proceso. */
private val Context.recyColPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "recycol_preferences",
)

/**
 * Implementación Android del puerto `KeyValueStore` sobre DataStore
 * Preferences (S36, RNF-014). Todo lo que se guarda aquí sobrevive al cierre
 * de la aplicación; nunca almacena frames ni datos de imagen (RNF-012).
 */
class DataStoreKeyValueStore(
    private val context: Context,
) : KeyValueStore {

    override suspend fun read(key: String): String? =
        context.recyColPreferences.data.first()[stringPreferencesKey(key)]

    override suspend fun write(key: String, value: String) {
        context.recyColPreferences.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value
        }
    }

    override suspend fun remove(key: String) {
        context.recyColPreferences.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }
}
