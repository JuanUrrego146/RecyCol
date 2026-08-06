package com.botabien.android.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.botabien.android.R
import com.botabien.android.ui.AppDependencies
import com.botabien.android.ui.components.BotaButton
import com.botabien.android.ui.components.BotaButtonStyle
import com.botabien.android.ui.components.BotaCard
import com.botabien.android.ui.country.countryDisplayName
import com.botabien.android.ui.theme.BotaTheme

/**
 * Pantalla de ajustes. En S05 contiene la fila de país (RF-003); las de
 * rendimiento e historial llegan con S08 sobre esta misma estructura.
 */
@Composable
fun SettingsScreen(
    dependencies: AppDependencies,
    onChangeCountry: () -> Unit,
    onBack: () -> Unit,
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
        Row {
            BotaButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                style = BotaButtonStyle.Plain,
                compact = true,
            )
        }
        Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
        Text(
            text = stringResource(R.string.settings_title),
            style = BotaTheme.typography.largeTitle,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xxl))

        BotaCard(onClick = onChangeCountry) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_country_label),
                    style = BotaTheme.typography.body,
                    color = BotaTheme.colors.label,
                    modifier = Modifier.weight(1f),
                )
                activeCountry?.let { country ->
                    Text(
                        text = country,
                        style = BotaTheme.typography.body,
                        color = BotaTheme.colors.secondaryLabel,
                    )
                }
                Spacer(modifier = Modifier.width(BotaTheme.spacing.sm))
                DisclosureChevron(color = BotaTheme.colors.tertiaryLabel)
            }
        }
    }
}

/** Chevron de navegación al estilo de las celdas de iOS. */
@Composable
private fun DisclosureChevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(12.dp)) {
        val stroke = size.minDimension * 0.16f
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.12f),
            end = Offset(size.width * 0.75f, size.height * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.75f, size.height * 0.50f),
            end = Offset(size.width * 0.35f, size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
