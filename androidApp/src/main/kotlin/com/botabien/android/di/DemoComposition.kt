package com.botabien.android.di

import com.botabien.domain.usecase.AdjustPerformanceUseCase
import com.botabien.domain.usecase.ClassifyWasteUseCase
import com.botabien.domain.usecase.ManageHistoryUseCase
import com.botabien.domain.usecase.ResolveManualDisposalUseCase
import com.botabien.domain.usecase.ScanBinsUseCase
import com.botabien.domain.usecase.SelectCountryUseCase
import com.botabien.rules.DefaultRuleEngine
import com.botabien.testing.FakeBinAvailabilityRepository
import com.botabien.testing.FakeBinDetector
import com.botabien.testing.FakeClassificationHistoryRepository
import com.botabien.testing.FakeFrameQualityAnalyzer
import com.botabien.testing.FakeProfileRepository
import com.botabien.testing.FakeTierPreferenceRepository
import com.botabien.testing.FakeWasteClassifier
import com.botabien.testing.TestProfiles

/**
 * Conjunto completo de casos de uso que consume la interfaz.
 *
 * @property classifyWaste clasificación por cámara (CUS-003 a CUS-006).
 * @property resolveManual selección manual y desambiguación (CUS-006).
 * @property manageHistory consulta y borrado del historial (CUS-009).
 * @property adjustPerformance preferencia manual de rendimiento (CUS-008).
 * @property scanBins escaneo y confirmación de canecas (CUS-002).
 * @property selectCountry configuración de país y perfil (CUS-001).
 */
class AppUseCases(
    val classifyWaste: ClassifyWasteUseCase,
    val resolveManual: ResolveManualDisposalUseCase,
    val manageHistory: ManageHistoryUseCase,
    val adjustPerformance: AdjustPerformanceUseCase,
    val scanBins: ScanBinsUseCase,
    val selectCountry: SelectCountryUseCase,
)

/**
 * Composición de la **Demo A** (issue #119, aprobada por Juan): clasificador
 * **simulado** y motor de reglas **real**.
 *
 * El recorrido calidad → clasificación → reglas → resultado es el de
 * producción; lo único simulado es la percepción (cámara-modelo) y la
 * persistencia. Cada fake se sustituye sin tocar pantallas cuando el módulo
 * real aterrice:
 *
 * - `FakeWasteClassifier`  → modelo real (EDGE + ML, M3/M4).
 * - `FakeFrameQualityAnalyzer` → métricas reales de CAM (#98).
 * - `FakeProfileRepository`    → catálogo real con `co.json` (RULES, S30).
 * - `FakeBinAvailabilityRepository`, `FakeClassificationHistoryRepository`
 *   → persistencia real de DATA (S36/S37).
 * - `FakeTierPreferenceRepository` → adaptador del TierStore de EDGE (#102).
 *
 * Composición manual y provisional: migra a módulos de Koin cuando la cola
 * de PRs drene (#119). `initiallyActive = null` reproduce el primer arranque:
 * el onboarding pide país antes de clasificar (RF-001).
 */
object DemoComposition {

    fun appUseCases(): AppUseCases {
        val profiles = FakeProfileRepository(
            catalog = listOf(TestProfiles.threeBins),
            initiallyActive = null,
        )
        val binAvailability = FakeBinAvailabilityRepository()
        val ruleEngine = DefaultRuleEngine()

        return AppUseCases(
            classifyWaste = ClassifyWasteUseCase(
                qualityAnalyzer = FakeFrameQualityAnalyzer(),
                classifier = FakeWasteClassifier(),
                ruleEngine = ruleEngine,
                profiles = profiles,
                binAvailability = binAvailability,
            ),
            resolveManual = ResolveManualDisposalUseCase(
                ruleEngine = ruleEngine,
                profiles = profiles,
                binAvailability = binAvailability,
            ),
            manageHistory = ManageHistoryUseCase(FakeClassificationHistoryRepository()),
            adjustPerformance = AdjustPerformanceUseCase(FakeTierPreferenceRepository()),
            scanBins = ScanBinsUseCase(
                detector = FakeBinDetector(),
                profiles = profiles,
                binAvailability = binAvailability,
            ),
            selectCountry = SelectCountryUseCase(
                profiles = profiles,
                binAvailability = binAvailability,
            ),
        )
    }
}
