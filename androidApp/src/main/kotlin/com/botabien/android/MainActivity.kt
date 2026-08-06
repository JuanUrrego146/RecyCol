package com.botabien.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.botabien.android.ui.theme.BotaBienTheme
import com.botabien.android.ui.theme.BotaTheme

/**
 * Punto de entrada de la aplicación. Desde S04 el árbol de composición
 * entero vive dentro de [BotaBienTheme]; la navegación real llega en S05.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BotaBienTheme {
                AppShell()
            }
        }
    }
}

@Composable
private fun AppShell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BotaTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = BotaTheme.typography.largeTitle,
            color = BotaTheme.colors.label,
        )
    }
}
