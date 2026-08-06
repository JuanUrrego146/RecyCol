package com.botabien.android.di

import com.botabien.android.ui.auth.SignInViewModel
import com.botabien.data.auth.GuestAuthProvider
import com.botabien.domain.port.AuthProvider
import com.botabien.domain.usecase.SignInUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Módulo Koin de autenticación (S38, agente DATA).
 *
 * En v1 el puerto lo satisface el stub de invitado: la app funciona completa
 * sin cuenta (RF-037). En v2 basta sustituir aquí el proveedor real
 * (previsto: Supabase) sin tocar la pantalla ni el caso de uso (RF-036).
 */
val authModule = module {
    single<AuthProvider> { GuestAuthProvider() }
    factory { SignInUseCase(get()) }
    viewModel { SignInViewModel(get()) }
}
