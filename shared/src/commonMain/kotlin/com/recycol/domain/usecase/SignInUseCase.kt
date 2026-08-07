package com.recycol.domain.usecase

import com.recycol.domain.model.Credentials
import com.recycol.domain.model.Session
import com.recycol.domain.port.AuthProvider

/**
 * Caso de uso de inicio de sesión (CUS-010; RF-035 a RF-037).
 *
 * La UI accede a la sesión únicamente por aquí, nunca por un proveedor
 * concreto (RF-036). En v1 el puerto lo implementa el stub de invitado y la
 * aplicación funciona completa sin cuenta (RF-037); en v2 el proveedor real
 * se sustituye por inyección de dependencias sin tocar este caso de uso.
 */
class SignInUseCase(
    private val authProvider: AuthProvider,
) {

    /** Sesión vigente; en v1 es siempre invitado. */
    suspend fun currentSession(): Session = authProvider.currentSession()

    /**
     * Intenta iniciar sesión. En v1 resuelve de forma determinista con el
     * fallo tipado de autenticación no disponible; la UI lo traduce al aviso
     * de versión futura (RF-035).
     */
    suspend fun signIn(credentials: Credentials): Result<Session> =
        authProvider.signIn(credentials)
}
