package com.recycol.testing

import com.recycol.domain.model.Credentials
import com.recycol.domain.model.Session
import com.recycol.domain.port.AuthProvider

/**
 * Fake determinista de `AuthProvider` (stub real: agente DATA, S38).
 *
 * Reproduce el comportamiento de la v1: la sesión vigente es siempre
 * [Session.Guest] y el inicio de sesión resuelve sin red con un resultado
 * fijado por constructor (éxito con un usuario sintético, por defecto).
 */
class FakeAuthProvider(
    private val signInResult: Result<Session> =
        Result.success(Session.Authenticated(userId = "user-fake-001")),
) : AuthProvider {

    override suspend fun currentSession(): Session = Session.Guest

    override suspend fun signIn(credentials: Credentials): Result<Session> = signInResult
}
