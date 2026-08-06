package com.botabien.data.auth

import com.botabien.domain.model.Credentials
import com.botabien.domain.model.Session
import com.botabien.domain.port.AuthProvider

/**
 * Señala que el inicio de sesión con credenciales aún no está disponible:
 * la aplicación opera en modo invitado con acceso completo (RF-037).
 * La UI la traduce a un mensaje de recursos de cadenas (RNF-011).
 */
class AuthUnavailableException : IllegalStateException(
    "El inicio de sesión con credenciales llega en una versión futura",
)

/**
 * Implementación v1 del puerto `AuthProvider` (S38; CUS-010, RF-036, RF-037).
 *
 * No hay backend en v1: la sesión vigente es siempre [Session.Guest] y todo
 * intento de inicio de sesión resuelve de forma determinista, sin red, con
 * [AuthUnavailableException]. En v2 el proveedor real (previsto: Supabase) la
 * reemplaza detrás del mismo puerto vía inyección de dependencias; ninguna
 * otra capa conoce al proveedor concreto ni su SDK (RF-036, RNF-005).
 */
class GuestAuthProvider : AuthProvider {

    override suspend fun currentSession(): Session = Session.Guest

    override suspend fun signIn(credentials: Credentials): Result<Session> =
        Result.failure(AuthUnavailableException())
}
