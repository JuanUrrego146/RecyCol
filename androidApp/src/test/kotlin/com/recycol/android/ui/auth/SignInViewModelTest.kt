package com.recycol.android.ui.auth

import com.recycol.domain.usecase.SignInUseCase
import com.recycol.testing.FakeAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato de la pantalla de sesión v1 (S38; RF-035): el intento de inicio
 * de sesión muestra el aviso de no disponible y editar cualquier campo lo
 * oculta, porque el aviso describe solo el intento anterior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SignInViewModel(
        signInUseCase = SignInUseCase(
            authProvider = FakeAuthProvider(
                signInResult = Result.failure(IllegalStateException("sin backend")),
            ),
        ),
    )

    @Test
    fun intentarIniciarSesionMuestraElAvisoDeNoDisponible() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onSignIn()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.unavailableNoticeVisible)
    }

    @Test
    fun editarElCorreoOcultaElAviso() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSignIn()
        advanceUntilIdle()

        viewModel.onEmailChange("nuevo@example.com")

        assertFalse(viewModel.uiState.value.unavailableNoticeVisible)
    }

    @Test
    fun editarLaContrasenaOcultaElAviso() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onSignIn()
        advanceUntilIdle()

        viewModel.onPasswordChange("otra")

        assertFalse(viewModel.uiState.value.unavailableNoticeVisible)
    }
}
