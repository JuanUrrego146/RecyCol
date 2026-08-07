package com.recycol.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.recycol.android.camera.HeuristicFrameQualityAnalyzer
import com.recycol.android.inference.bins.ColorRegionBinDetector
import com.recycol.domain.port.BinAvailabilityRepository
import com.recycol.domain.port.ClassificationHistoryRepository
import com.recycol.domain.port.ProfileRepository
import com.recycol.domain.port.TierPreferenceRepository
import com.recycol.domain.port.WasteClassifier
import com.recycol.domain.usecase.AdjustPerformanceUseCase
import com.recycol.domain.usecase.ClassifyWasteUseCase
import com.recycol.domain.usecase.ManageHistoryUseCase
import com.recycol.domain.usecase.ResolveManualDisposalUseCase
import com.recycol.domain.usecase.ScanBinsUseCase
import com.recycol.domain.usecase.SelectCountryUseCase
import com.recycol.rules.DefaultRuleEngine
import org.koin.compose.koinInject

/**
 * Casos de uso que consume la capa de interfaz. La UI nunca llama a
 * repositorios ni a inferencia directamente (invariante 4): todo pasa por
 * estos casos de uso de `shared/domain/usecase/`.
 *
 * @property selectCountry configuración de país y perfil (CUS-001); al cambiar
 *   de país reinicia por sí solo las canecas confirmadas (coordinación #65).
 * @property scanBins escaneo y confirmación de canecas (CUS-002).
 * @property classifyWaste clasificación por cámara (CUS-003 a CUS-006).
 * @property resolveManualDisposal selección manual de material (RF-024,
 *   RF-025 · CUS-006, coordinación #94).
 * @property adjustPerformance ajuste manual del nivel de rendimiento (RF-031).
 * @property manageHistory consulta y borrado del historial (RF-032 a RF-034).
 */
class AppDependencies(
    val selectCountry: SelectCountryUseCase,
    val scanBins: ScanBinsUseCase,
    val classifyWaste: ClassifyWasteUseCase,
    val resolveManualDisposal: ResolveManualDisposalUseCase,
    val adjustPerformance: AdjustPerformanceUseCase,
    val manageHistory: ManageHistoryUseCase,
)

/**
 * Composición de producción: casos de uso sobre las implementaciones reales
 * de todos los agentes, resueltas por Koin.
 *
 * Reemplaza la composición provisional sobre `shared/testing/` (issue de
 * cierre de v1): `ProfileRepository` (DATA+RULES, #48), `WasteClassifier`
 * (EDGE, modelos reales de M4), `BinDetector` (BINS) y `FrameQualityAnalyzer`
 * (CAM) — cero fakes en el APK de producción. `RuleEngine` no cambia: la
 * composición provisional ya usaba `DefaultRuleEngine`, la implementación
 * real, porque nunca fue el cuello de botella (M5 cerrado desde el principio).
 */
@Composable
fun rememberAppDependencies(): AppDependencies {
    val profiles = koinInject<ProfileRepository>()
    val binAvailability = koinInject<BinAvailabilityRepository>()
    val classifier = koinInject<WasteClassifier>()
    val history = koinInject<ClassificationHistoryRepository>()
    val tierPreference = koinInject<TierPreferenceRepository>()

    return remember(profiles, binAvailability, classifier, history, tierPreference) {
        val ruleEngine = DefaultRuleEngine()
        AppDependencies(
            selectCountry = SelectCountryUseCase(
                profiles = profiles,
                binAvailability = binAvailability,
            ),
            scanBins = ScanBinsUseCase(
                detector = ColorRegionBinDetector(profiles),
                profiles = profiles,
                binAvailability = binAvailability,
            ),
            classifyWaste = ClassifyWasteUseCase(
                qualityAnalyzer = HeuristicFrameQualityAnalyzer(),
                classifier = classifier,
                ruleEngine = ruleEngine,
                profiles = profiles,
                binAvailability = binAvailability,
            ),
            resolveManualDisposal = ResolveManualDisposalUseCase(
                ruleEngine = ruleEngine,
                profiles = profiles,
                binAvailability = binAvailability,
            ),
            adjustPerformance = AdjustPerformanceUseCase(tierPreference),
            manageHistory = ManageHistoryUseCase(history),
        )
    }
}
