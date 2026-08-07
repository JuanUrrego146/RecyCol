package com.botabien.data.auth

import com.botabien.domain.model.Credentials
import com.botabien.domain.model.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato del stub de autenticación v1 (S38; CUS-010): siempre invitado,
 * sin red y determinista, con el fallo tipado que la UI traduce a recursos.
 */
class GuestAuthProviderTest {

    private val provider = GuestAuthProvider()

    @Test
    fun laSesionVigenteEsSiempreInvitado() = runTest {
        assertEquals(Session.Guest, provider.currentSession())
    }

    @Test
    fun elInicioDeSesionFallaConElAvisoDeVersionFutura() = runTest {
        val result = provider.signIn(Credentials(email = "user@example.com", password = "secreta"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AuthUnavailableException)
    }

    @Test
    fun elResultadoEsDeterministaParaCualquierCredencial() = runTest {
        val first = provider.signIn(Credentials(email = "a@example.com", password = "x"))
        val second = provider.signIn(Credentials(email = "b@example.com", password = "y"))

        assertTrue(first.isFailure)
        assertTrue(second.isFailure)
        assertEquals(Session.Guest, provider.currentSession())
    }
}
