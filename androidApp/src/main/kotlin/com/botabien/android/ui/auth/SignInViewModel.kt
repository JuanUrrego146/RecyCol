package com.botabien.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.botabien.domain.model.Credentials
import com.botabien.domain.usecase.SignInUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orquesta la pantalla de inicio de sesión (S38; CUS-010, RF-035).
 *
 * Solo coordina el caso de uso: sin lógica de negocio (regla 4 de la
 * arquitectura). En v1 el intento de inicio de sesión resuelve siempre con el
 * fallo de autenticación no disponible y la pantalla muestra el aviso de
 * versión futura; el éxito quedará cableado cuando exista el backend (v2).
 */
class SignInViewModel(
    private val signInUseCase: SignInUseCase,
) : ViewModel() {

    /**
     * Estado de la pantalla. Las credenciales viven solo en memoria mientras
     * la pantalla existe: nunca se persisten ni se registran en logs.
     */
    data class UiState(
        val email: String = "",
        val password: String = "",
        val unavailableNoticeVisible: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onSignIn() {
        viewModelScope.launch {
            val state = _uiState.value
            signInUseCase.signIn(Credentials(email = state.email, password = state.password))
                .onFailure {
                    _uiState.update { it.copy(unavailableNoticeVisible = true) }
                }
        }
    }
}
