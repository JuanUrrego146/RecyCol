package com.botabien.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.botabien.android.camera.CameraXFrameSource
import com.botabien.android.ui.classify.CameraViewfinder
import com.botabien.android.ui.classify.ClassifyScreen
import com.botabien.android.ui.components.BotaActivityIndicator
import com.botabien.android.ui.country.CountrySelectionScreen
import com.botabien.android.ui.navigation.AppDestination
import com.botabien.android.ui.navigation.AppNavHost
import com.botabien.android.ui.navigation.AppNavState
import com.botabien.android.ui.result.ResultDetailScreen
import com.botabien.android.ui.settings.SettingsScreen
import com.botabien.android.ui.theme.BotaTheme

/**
 * Raíz de la aplicación: resuelve el destino inicial (onboarding si no hay
 * perfil activo, RF-001) y monta el grafo de navegación. Las dependencias
 * son los casos de uso del dominio, hoy cableados sobre los fakes
 * ([fakeAppDependencies]) hasta que RULES y DATA publiquen implementaciones.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val dependencies = remember { fakeAppDependencies() }
    val appContext = LocalContext.current.applicationContext
    val frameSource = remember { CameraXFrameSource(appContext) }
    var start by remember { mutableStateOf<AppDestination?>(null) }
    LaunchedEffect(dependencies) {
        start = if (dependencies.selectCountry.activeProfileOrNull() == null) {
            AppDestination.Onboarding
        } else {
            AppDestination.Home
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BotaTheme.colors.background),
    ) {
        when (val startDestination = start) {
            null -> BotaActivityIndicator(
                modifier = Modifier.align(Alignment.Center),
            )

            else -> {
                val navState = remember(startDestination) { AppNavState(startDestination) }
                AppNavHost(navState = navState) { destination ->
                    when (destination) {
                        AppDestination.Onboarding -> CountrySelectionScreen(
                            dependencies = dependencies,
                            isOnboarding = true,
                            onCountryApplied = { navState.replaceAll(AppDestination.Home) },
                        )

                        AppDestination.Home -> ClassifyScreen(
                            dependencies = dependencies,
                            frames = frameSource.frames,
                            viewfinder = { CameraViewfinder(frameSource, it) },
                            onOpenSettings = { navState.push(AppDestination.Settings) },
                            onOpenResultDetail = { outcome ->
                                navState.push(AppDestination.ResultDetail(outcome))
                            },
                        )

                        AppDestination.Settings -> SettingsScreen(
                            dependencies = dependencies,
                            onChangeCountry = { navState.push(AppDestination.CountryChange) },
                            onBack = { navState.pop() },
                        )

                        AppDestination.CountryChange -> CountrySelectionScreen(
                            dependencies = dependencies,
                            isOnboarding = false,
                            onCountryApplied = { navState.pop() },
                        )

                        is AppDestination.ResultDetail -> ResultDetailScreen(
                            dependencies = dependencies,
                            outcome = destination.outcome,
                            onBack = { navState.pop() },
                        )
                    }
                }
            }
        }
    }
}
