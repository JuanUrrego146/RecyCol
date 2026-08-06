package com.botabien.android.ui.country

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.botabien.android.R
import com.botabien.android.ui.AppDependencies
import com.botabien.android.ui.components.BotaActivityIndicator
import com.botabien.android.ui.components.BotaButton
import com.botabien.android.ui.components.BotaCard
import com.botabien.android.ui.theme.BotaTheme
import com.botabien.domain.model.CountryProfile
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado de la selección de país (CUS-001). Orquesta exclusivamente casos de
 * uso: el catálogo y la activación pasan por `SelectCountryUseCase` y el
 * reinicio del conjunto de canecas por `ScanBinsUseCase` (RF-003); ninguna
 * lógica normativa vive aquí.
 */
@Stable
class CountrySelectionState(
    private val dependencies: AppDependencies,
    private val scope: CoroutineScope,
) {

    /** Catálogo de perfiles; `null` mientras carga. */
    var countries: List<CountryProfile>? by mutableStateOf(null)
        private set

    /** País marcado por el usuario, aún sin confirmar. */
    var selectedIso: String? by mutableStateOf(null)
        private set

    /** Verdadero mientras se aplica la selección confirmada. */
    var applying: Boolean by mutableStateOf(false)
        private set

    /** Carga el catálogo y premarca el perfil activo si existe. */
    fun load() {
        scope.launch {
            selectedIso = dependencies.selectCountry.activeProfileOrNull()?.isoCode
            countries = dependencies.selectCountry.availableCountries()
        }
    }

    /** Marca un país de la lista. */
    fun select(isoCode: String) {
        selectedIso = isoCode
    }

    /**
     * Confirma la selección y activa el perfil. El reinicio del conjunto de
     * canecas lo hace el propio caso de uso (RF-003, coordinación #65).
     */
    fun confirm(onApplied: () -> Unit) {
        val isoCode = selectedIso ?: return
        if (applying) return
        scope.launch {
            applying = true
            try {
                dependencies.selectCountry.select(isoCode)
            } finally {
                applying = false
            }
            onApplied()
        }
    }
}

/**
 * Pantalla de selección de país (RF-001, RF-003). Sirve al onboarding del
 * primer arranque y al cambio desde ajustes; solo cambian los textos.
 *
 * @param isOnboarding `true` en el primer arranque.
 * @param onCountryApplied se invoca tras confirmar y recargar el perfil.
 */
@Composable
fun CountrySelectionScreen(
    dependencies: AppDependencies,
    isOnboarding: Boolean,
    onCountryApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = remember { CountrySelectionState(dependencies, scope) }
    LaunchedEffect(state) {
        state.load()
    }

    val countries = state.countries
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = BotaTheme.spacing.screenMargin,
                end = BotaTheme.spacing.screenMargin,
                top = BotaTheme.spacing.xxxl,
                bottom = BotaTheme.spacing.xxl,
            ),
    ) {
        Text(
            text = stringResource(R.string.country_selection_title),
            style = BotaTheme.typography.largeTitle,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
        Text(
            text = stringResource(R.string.country_selection_subtitle),
            style = BotaTheme.typography.subheadline,
            color = BotaTheme.colors.secondaryLabel,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xxxl))

        if (countries == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BotaActivityIndicator(
                    contentDescription = stringResource(R.string.loading_description),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BotaTheme.spacing.md),
            ) {
                countries.forEach { profile ->
                    CountryRow(
                        profile = profile,
                        selected = profile.isoCode == state.selectedIso,
                        onSelect = { state.select(profile.isoCode) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(BotaTheme.spacing.xxl))
            BotaButton(
                text = stringResource(
                    if (isOnboarding) R.string.action_continue else R.string.action_change_country
                ),
                onClick = { state.confirm(onCountryApplied) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.selectedIso != null && !state.applying,
            )
        }
    }
}

/** Fila de país: nombre localizado, norma que aplica y marca de selección. */
@Composable
private fun CountryRow(
    profile: CountryProfile,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val selectedDescription = stringResource(R.string.country_selected_description)
    BotaCard(onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = countryDisplayName(profile.isoCode),
                    style = BotaTheme.typography.headline,
                    color = BotaTheme.colors.label,
                )
                Spacer(modifier = Modifier.height(BotaTheme.spacing.xxs))
                Text(
                    text = profile.regulationName,
                    style = BotaTheme.typography.footnote,
                    color = BotaTheme.colors.secondaryLabel,
                )
            }
            if (selected) {
                SelectionCheck(
                    color = BotaTheme.colors.accent,
                    modifier = Modifier.semantics { contentDescription = selectedDescription },
                )
            }
        }
    }
}

/** Marca de verificación dibujada con los tokens del design system. */
@Composable
private fun SelectionCheck(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = size.minDimension * 0.14f
        drawLine(
            color = color,
            start = Offset(size.width * 0.12f, size.height * 0.55f),
            end = Offset(size.width * 0.40f, size.height * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.40f, size.height * 0.82f),
            end = Offset(size.width * 0.88f, size.height * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Nombre localizado del país a partir del código ISO del perfil. El nombre es
 * dato derivado del código, no un literal de código (RNF-011); si la región
 * no existe (perfiles sintéticos de prueba), se muestra el código en mayúsculas.
 */
internal fun countryDisplayName(isoCode: String): String {
    val locale = Locale.Builder().setRegion(isoCode).build()
    val name = locale.displayCountry
    return if (name.isBlank() || name.equals(isoCode, ignoreCase = true)) {
        isoCode.uppercase(Locale.getDefault())
    } else {
        name
    }
}
