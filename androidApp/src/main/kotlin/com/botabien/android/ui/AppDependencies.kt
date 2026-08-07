package com.botabien.android.ui

import com.botabien.domain.model.ClassificationOutcome
import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.WasteMaterial
import com.botabien.android.ui.settings.InMemoryPerformancePreference
import com.botabien.android.ui.settings.PerformancePreference
import com.botabien.domain.port.ClassificationHistoryRepository
import com.botabien.domain.usecase.ClassifyWasteUseCase
import com.botabien.domain.usecase.ScanBinsUseCase
import com.botabien.domain.usecase.SelectCountryUseCase
import com.botabien.rules.DefaultRuleEngine
import com.botabien.testing.FakeBinAvailabilityRepository
import com.botabien.testing.FakeClassificationHistoryRepository
import com.botabien.testing.FakeBinDetector
import com.botabien.testing.FakeFrameQualityAnalyzer
import com.botabien.testing.FakeProfileRepository
import com.botabien.testing.FakeWasteClassifier
import com.botabien.testing.TestProfiles

/**
 * Casos de uso que consume la capa de interfaz. La UI nunca llama a
 * repositorios ni a inferencia directamente (invariante 4): todo pasa por
 * estos casos de uso de `shared/domain/usecase/`.
 *
 * @property selectCountry configuración de país y perfil (CUS-001); al cambiar
 *   de país reinicia por sí solo las canecas confirmadas (coordinación #65).
 * @property scanBins escaneo y confirmación de canecas (CUS-002).
 * @property classifyWaste clasificación por cámara (CUS-003 a CUS-006).
 * @property resolveManualDisposal resolución de una selección manual de
 *   material (RF-024, RF-025 · CUS-006).
 * @property performance preferencia manual del nivel de rendimiento (RF-031);
 *   seam provisional alineado al TierStore de EDGE (coordinación #94).
 * @property history historial local (RF-032 a RF-034); puerto del contrato,
 *   pendiente del caso de uso de la coordinación #94.
 */
class AppDependencies(
    val selectCountry: SelectCountryUseCase,
    val scanBins: ScanBinsUseCase,
    val classifyWaste: ClassifyWasteUseCase,
    val resolveManualDisposal: ManualDisposalResolver,
    val performance: PerformancePreference,
    val history: ClassificationHistoryRepository,
)

/**
 * Resolución de la selección manual de material (CUS-006), aprobada por Juan:
 * el usuario elige el material —por baja confianza o por decisión propia— y
 * recibe la caneca según la normativa (p. ej. ELECTRONIC → punto de
 * recolección especial).
 *
 * Seam provisional hasta el caso de uso de la coordinación #94: la decisión
 * la toma íntegramente el RuleEngine con el perfil activo y las canecas
 * disponibles (invariante 2); aquí no vive ninguna regla propia.
 */
fun interface ManualDisposalResolver {
    suspend fun resolve(material: WasteMaterial): ClassificationOutcome
}

/**
 * Composición provisional sobre los fakes deterministas de `shared/testing/`,
 * siguiendo el modelo de trabajo entre agentes: nadie espera a nadie. Cuando
 * los agentes RULES (S30, catálogo real) y DATA (S36, persistencia) publiquen
 * sus implementaciones, este cableado se sustituye por los módulos de Koin
 * sin tocar ninguna pantalla.
 *
 * `initiallyActive = null` reproduce el primer arranque: no hay perfil activo
 * hasta que el usuario elige país en el onboarding (RF-001).
 */
fun fakeAppDependencies(): AppDependencies {
    val profiles = FakeProfileRepository(
        catalog = listOf(TestProfiles.threeBins),
        initiallyActive = null,
    )
    val binAvailability = FakeBinAvailabilityRepository()
    val ruleEngine = DefaultRuleEngine()
    return AppDependencies(
        selectCountry = SelectCountryUseCase(
            profiles = profiles,
            binAvailability = binAvailability,
        ),
        scanBins = ScanBinsUseCase(
            detector = FakeBinDetector(),
            profiles = profiles,
            binAvailability = binAvailability,
        ),
        classifyWaste = ClassifyWasteUseCase(
            qualityAnalyzer = FakeFrameQualityAnalyzer(),
            classifier = FakeWasteClassifier(),
            ruleEngine = ruleEngine,
            profiles = profiles,
            binAvailability = binAvailability,
        ),
        resolveManualDisposal = ManualDisposalResolver { material ->
            val profile = checkNotNull(profiles.activeProfileOrNull()) {
                "No hay perfil normativo activo: el onboarding no se completó"
            }
            val disposal = ruleEngine.resolve(
                material = material,
                contamination = ContaminationState.UNKNOWN,
                availableBins = binAvailability.availableBins(),
                profile = profile,
            )
            ClassificationOutcome(
                classification = ClassificationResult(material, confidence = 1f),
                disposal = disposal,
                hints = emptyList(),
                needsUserDecision = false,
            )
        },
        performance = InMemoryPerformancePreference(),
        history = FakeClassificationHistoryRepository(),
    )
}
