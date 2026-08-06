package com.botabien.domain.usecase

import com.botabien.data.auth.AuthUnavailableException
import com.botabien.data.auth.GuestAuthProvider
import com.botabien.domain.model.Credentials
import com.botabien.domain.model.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CUS-010 con el stub v1 detrás del puerto: la sesión es siempre invitado y
 * el intento de inicio de sesión produce el fallo tipado que la pantalla
 * traduce al aviso de versión futura (RF-035, RF-037).
 */
class SignInUseCaseTest {

    private val useCase = SignInUseCase(authProvider = GuestAuthProvider())

    @Test
    fun laSesionVigenteEsInvitado() = runTest {
        assertEquals(Session.Guest, useCase.currentSession())
    }

    @Test
    fun elIntentoDeInicioDeSesionProduceElFalloDeNoDisponible() = runTest {
        val result = useCase.signIn(Credentials(email = "user@example.com", password = "x"))

        assertTrue(result.exceptionOrNull() is AuthUnavailableException)
    }
}
