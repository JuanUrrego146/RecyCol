package com.recycol.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Puerto de creación del driver de base de datos (S36, RNF-005).
 *
 * El esquema y los repositorios son multiplataforma; el driver concreto no:
 * Android usa `AndroidSqliteDriver` (en `androidApp/`), las pruebas JVM usan
 * el driver JDBC sobre archivo y la fase iOS aportará el driver nativo.
 */
fun interface DatabaseDriverFactory {

    /** Crea un driver listo para usar, con el esquema ya creado o migrado. */
    fun createDriver(): SqlDriver
}

/** Construye la base de datos local sobre el driver de la plataforma. */
fun createDatabase(driverFactory: DatabaseDriverFactory): RecyColDatabase =
    RecyColDatabase(driverFactory.createDriver())
