package com.recycol.android.ui

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
import com.recycol.android.camera.CameraXFrameSource
import com.recycol.android.inference.tier.BenchmarkedTierPolicy
import com.recycol.android.ui.classify.CameraViewfinder
import com.recycol.android.ui.classify.ClassifyScreen
import com.recycol.android.ui.components.BotaActivityIndicator
import com.recycol.android.ui.country.CountrySelectionScreen
import com.recycol.android.ui.navigation.AppDestination
import com.recycol.android.ui.navigation.AppNavHost
import com.recycol.android.ui.navigation.AppNavState
import com.recycol.android.ui.result.ResultDetailScreen
import com.recycol.android.ui.settings.SettingsScreen
import com.recycol.android.ui.theme.BotaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Raíz de la aplicación: resuelve el destino inicial (onboarding si no hay
 * perfil activo, RF-001) y monta el grafo de navegación. Las dependencias
 * son los casos de uso del dominio sobre las implementaciones reales de
 * todos los agentes ([rememberAppDependencies], resueltas por Koin).
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val dependencies = rememberAppDependencies()
    val tierPolicy = koinInject<BenchmarkedTierPolicy>()
    val appContext = LocalContext.current.applicationContext
    val frameSource = remember { CameraXFrameSource(appContext) }
    var start by remember { mutableStateOf<AppDestination?>(null) }
    LaunchedEffect(tierPolicy) {
        // Una vez por arranque, fuera del hilo principal (RF-029): con caché
        // válida es no-op; en el primer arranque corre el micro-benchmark
        // dentro del presupuesto de 2 s. No bloquea el destino inicial.
        withContext(Dispatchers.Default) { tierPolicy.ensureResolved() }
    }
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
