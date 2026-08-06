package com.botabien.android.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.botabien.android.ui.theme.BotaMotion

/**
 * Destinos de la aplicación. El grafo refleja la máquina de estados de la
 * sesión (docs/arquitectura.md): onboarding → listo, con ajustes y cambio de
 * país como pila. Las pantallas de cámara (S06), resultado (S07) y el resto
 * de ajustes (S08) se incorporan como destinos nuevos sin cambiar el esquema.
 */
sealed interface AppDestination {

    /** Primer arranque: selección de país (RF-001, CUS-001). */
    data object Onboarding : AppDestination

    /** Pantalla principal; hasta S06, un anfitrión provisional. */
    data object Home : AppDestination

    /** Ajustes (S08 la completa; aquí vive el cambio de país, RF-003). */
    data object Settings : AppDestination

    /** Selección de país desde ajustes: recarga el perfil activo. */
    data object CountryChange : AppDestination
}

/**
 * Pila de navegación observable. Una lista de snapshots basta para este
 * grafo y permite transiciones a medida sin dependencias nuevas; si el grafo
 * creciera en complejidad, migrar a Navigation Compose sería una issue propia.
 */
class AppNavState(start: AppDestination) {

    private val backStack = mutableStateListOf(start)

    /** Destino visible. */
    val current: AppDestination get() = backStack.last()

    /** Verdadero si hay un destino debajo al que volver. */
    val canPop: Boolean get() = backStack.size > 1

    /** Profundidad actual de la pila; la usa el host para animar push frente a pop. */
    val depth: Int get() = backStack.size

    /** Empuja un destino sobre la pila. */
    fun push(destination: AppDestination) {
        backStack.add(destination)
    }

    /** Vuelve al destino anterior; no hace nada en la raíz. */
    fun pop() {
        if (canPop) backStack.removeAt(backStack.lastIndex)
    }

    /** Sustituye la pila entera; para salir del onboarding hacia la app. */
    fun replaceAll(destination: AppDestination) {
        backStack.clear()
        backStack.add(destination)
    }
}

/**
 * Anfitrión de navegación con la transición de empuje de iOS: la pantalla
 * entrante se desliza desde el borde final mientras la saliente se aparta un
 * cuarto de su ancho; al volver, el gesto se invierte (RNF-009). El botón
 * atrás del sistema hace pop mientras haya pila.
 */
@Composable
fun AppNavHost(
    navState: AppNavState,
    modifier: Modifier = Modifier,
    content: @Composable (AppDestination) -> Unit,
) {
    BackHandler(enabled = navState.canPop) {
        navState.pop()
    }

    AnimatedContent(
        targetState = navState.current to navState.depth,
        modifier = modifier,
        contentKey = { it.first },
        transitionSpec = {
            val pushing = targetState.second >= initialState.second
            val spec = tween<Float>(BotaMotion.DURATION_BASE_MS, easing = BotaMotion.easeInOut)
            val slide = tween<IntOffset>(
                BotaMotion.DURATION_BASE_MS,
                easing = BotaMotion.easeInOut,
            )
            if (pushing) {
                (slideInHorizontally(slide) { it } + fadeIn(spec, initialAlpha = 0.9f))
                    .togetherWith(slideOutHorizontally(slide) { -it / 4 } + fadeOut(spec, targetAlpha = 0.9f))
            } else {
                (slideInHorizontally(slide) { -it / 4 } + fadeIn(spec, initialAlpha = 0.9f))
                    .togetherWith(slideOutHorizontally(slide) { it } + fadeOut(spec, targetAlpha = 0.9f))
            }
        },
        label = "appNavHost",
    ) { (destination, _) ->
        content(destination)
    }
}
