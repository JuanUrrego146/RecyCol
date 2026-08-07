package com.recycol.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.recycol.android.ui.AppRoot
import com.recycol.android.ui.launch.BotaLaunchScreen
import com.recycol.android.ui.theme.RecyColTheme

/**
 * Punto de entrada de la aplicación. El árbol entero vive dentro de
 * [RecyColTheme]; el grafo de navegación y el arranque condicionado por el
 * onboarding se resuelven en [AppRoot] (S05).
 *
 * [BotaLaunchScreen] envuelve la raíz en vez de precederla: el contenido se
 * compone desde el primer fotograma por debajo del velo de marca, de modo que
 * la entrada tape el arranque sin alargarlo.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecyColTheme {
                BotaLaunchScreen {
                    AppRoot()
                }
            }
        }
    }
}
