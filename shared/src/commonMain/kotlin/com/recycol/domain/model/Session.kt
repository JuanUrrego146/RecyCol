package com.recycol.domain.model

/**
 * Sesión de usuario. En v1 no hay backend: la app funciona completa como
 * invitado y `AuthProvider` devuelve siempre [Guest] (CUS-010).
 */
sealed interface Session {
    /** Sesión de invitado: estado único de la v1. */
    data object Guest : Session

    /**
     * Sesión autenticada, reservada para la fase con backend real.
     * @property userId identificador opaco del usuario.
     */
    data class Authenticated(val userId: String) : Session
}

/**
 * Credenciales de inicio de sesión. Solo las consume el stub de `AuthProvider`
 * en v1; nunca se persisten ni se registran en logs.
 *
 * @property email correo del usuario.
 * @property password contraseña en claro solo en tránsito hacia el proveedor.
 */
data class Credentials(
    val email: String,
    val password: String,
)
