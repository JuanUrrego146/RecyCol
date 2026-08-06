package com.botabien.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.botabien.android.ui.AppRoot
import com.botabien.android.ui.theme.BotaBienTheme

/**
 * Punto de entrada de la aplicación. El árbol entero vive dentro de
 * [BotaBienTheme]; el grafo de navegación y el arranque condicionado por el
 * onboarding se resuelven en [AppRoot] (S05).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BotaBienTheme {
                AppRoot()
            }
        }
    }
}
