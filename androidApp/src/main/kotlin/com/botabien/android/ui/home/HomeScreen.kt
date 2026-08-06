package com.botabien.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.botabien.android.R
import com.botabien.android.ui.AppDependencies
import com.botabien.android.ui.components.BotaButton
import com.botabien.android.ui.components.BotaButtonStyle
import com.botabien.android.ui.components.BotaStatusPill
import com.botabien.android.ui.components.BotaStatusTone
import com.botabien.android.ui.country.countryDisplayName
import com.botabien.android.ui.theme.BotaTheme

/**
 * Anfitrión provisional de la pantalla principal. La pantalla de cámara real
 * (S06, RF-009) sustituirá el contenido central; la estructura —perfil activo
 * visible y acceso a ajustes— permanece.
 */
@Composable
fun HomeScreen(
    dependencies: AppDependencies,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeCountry by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dependencies) {
        activeCountry = dependencies.selectCountry.activeProfileOrNull()
            ?.let { countryDisplayName(it.isoCode) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = BotaTheme.spacing.screenMargin,
                vertical = BotaTheme.spacing.lg,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            BotaButton(
                text = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
                style = BotaButtonStyle.Plain,
                compact = true,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = BotaTheme.typography.largeTitle,
                color = BotaTheme.colors.label,
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.md))
            activeCountry?.let { country ->
                BotaStatusPill(
                    text = country,
                    tone = BotaStatusTone.Accent,
                )
            }
        }
    }
}
