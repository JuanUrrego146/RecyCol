package com.botabien.rules.profile

/**
 * Acceso de solo lectura a los archivos del catálogo de perfiles normativos.
 *
 * `shared/` no conoce ninguna plataforma (RNF-005): cada plataforma implementa
 * esta interfaz sobre su mecanismo de recursos (assets en Android, bundle en
 * iOS) y las pruebas usan una implementación en memoria. Los perfiles son
 * recursos locales empaquetados: leerlos jamás toca la red (RNF-002).
 */
fun interface ProfileSource {

    /**
     * Devuelve el contenido del archivo [fileName] del catálogo de perfiles,
     * o `null` si el archivo no existe.
     */
    fun read(fileName: String): String?
}
