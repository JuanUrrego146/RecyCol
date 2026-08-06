package com.botabien.android.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.botabien.data.db.BotaBienDatabase
import com.botabien.data.db.DatabaseDriverFactory

/**
 * Driver SQLite de Android para la base de datos local (S36, RNF-014).
 * El esquema y los repositorios viven en `shared/data`; aquí solo se aporta
 * el driver de la plataforma (RNF-005).
 */
class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {

    override fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = BotaBienDatabase.Schema,
            context = context,
            name = "botabien.db",
        )
}
