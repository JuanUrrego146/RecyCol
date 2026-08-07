package com.recycol.testing

import com.recycol.domain.model.Credentials
import com.recycol.domain.model.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Documenta el contrato del fake de `AuthProvider`: comportamiento v1
 * (siempre invitado) con inicio de sesión determinista y sin red.
 */
class FakeAuthProviderTest {

    @Test
    fun laSesionVigenteEsSiempreInvitado() = runTest {
        val provider = FakeAuthProvider()

        assertEquals(Session.Guest, provider.currentSession())
    }

    @Test
    fun elInicioDeSesionResuelveConElResultadoConfigurado() = runTest {
        val provider = FakeAuthProvider()

        val result = provider.signIn(Credentials(email = "test@example.com", password = "irrelevante"))

        assertTrue(result.isSuccess)
        assertEquals(Session.Authenticated(userId = "user-fake-001"), result.getOrNull())
    }

    @Test
    fun puedeConfigurarseParaSimularUnFalloDeAutenticacion() = runTest {
        val provider = FakeAuthProvider(
            signInResult = Result.failure(IllegalStateException("credenciales inválidas")),
        )

        val result = provider.signIn(Credentials(email = "test@example.com", password = "x"))

        assertTrue(result.isFailure)
    }
}
