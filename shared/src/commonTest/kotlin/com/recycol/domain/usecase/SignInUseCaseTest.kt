package com.recycol.domain.usecase

import com.recycol.domain.model.Credentials
import com.recycol.domain.model.Session
import com.recycol.domain.port.AuthProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato del caso de uso de sesión (CUS-010): delega en el puerto sin
 * lógica propia (RF-036). Se prueba contra un fake local del puerto, no
 * contra una implementación concreta: el caso de uso no debe conocer ninguna.
 */
class SignInUseCaseTest {

    /** Fake mínimo del puerto, configurable por resultado. */
    private class ConfigurableAuthProvider(
        private val session: Session = Session.Guest,
        private val signInResult: Result<Session> =
            Result.failure(IllegalStateException("sin backend")),
    ) : AuthProvider {
        override suspend fun currentSession(): Session = session
        override suspend fun signIn(credentials: Credentials): Result<Session> = signInResult
    }

    @Test
    fun laSesionVigenteEsLaDelProveedor() = runTest {
        val useCase = SignInUseCase(authProvider = ConfigurableAuthProvider())

        assertEquals(Session.Guest, useCase.currentSession())
    }

    @Test
    fun elResultadoDelInicioDeSesionEsElDelProveedor() = runTest {
        val failure = SignInUseCase(authProvider = ConfigurableAuthProvider())
        val success = SignInUseCase(
            authProvider = ConfigurableAuthProvider(
                signInResult = Result.success(Session.Authenticated(userId = "user-001")),
            ),
        )

        assertTrue(failure.signIn(Credentials(email = "a@example.com", password = "x")).isFailure)
        assertEquals(
            Session.Authenticated(userId = "user-001"),
            success.signIn(Credentials(email = "a@example.com", password = "x")).getOrNull(),
        )
    }
}
