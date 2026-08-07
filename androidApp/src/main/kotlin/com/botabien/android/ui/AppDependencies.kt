package com.botabien.android.ui

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.port.TierPreferenceRepository
import com.botabien.domain.usecase.AdjustPerformanceUseCase
import com.botabien.domain.usecase.ClassifyWasteUseCase
import com.botabien.domain.usecase.ManageHistoryUseCase
import com.botabien.domain.usecase.ResolveManualDisposalUseCase
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
    // El perfil de prueba viene con un código ISO inventado, y eso hacía que
    // la primera pantalla de la aplicación —y luego la barra superior— dijeran
    // «Región desconocida». Se le pone el de Colombia para que la composición
    // provisional se parezca a lo que verá el usuario; el contenido normativo
    // del perfil no cambia y sigue siendo el de `shared/testing`.
    val profiles = FakeProfileRepository(
        catalog = listOf(TestProfiles.threeBins.copy(isoCode = DEMO_ISO_CODE)),
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
        resolveManualDisposal = ResolveManualDisposalUseCase(
            ruleEngine = ruleEngine,
            profiles = profiles,
            binAvailability = binAvailability,
        ),
        adjustPerformance = AdjustPerformanceUseCase(InMemoryTierPreference()),
        manageHistory = ManageHistoryUseCase(FakeClassificationHistoryRepository()),
    )
}

/**
 * Doble en memoria de [TierPreferenceRepository] para la composición sobre
 * fakes: la implementación real es el adaptador de EDGE sobre su TierStore
 * (S18) y persiste entre reinicios. Se retira cuando `shared/testing` ofrezca
 * un FakeTierPreferenceRepository (pedido en la revisión del PR #96).
 */
private class InMemoryTierPreference : TierPreferenceRepository {

    private var value: DeviceTier? = null

    override suspend fun manualOverride(): DeviceTier? = value

    override suspend fun setManualOverride(tier: DeviceTier?) {
        value = tier
    }
}

/**
 * País de la composición provisional. Desaparece con los fakes, cuando RULES
 * publique el catálogo real y el usuario elija de verdad entre países.
 */
private const val DEMO_ISO_CODE = "CO"
