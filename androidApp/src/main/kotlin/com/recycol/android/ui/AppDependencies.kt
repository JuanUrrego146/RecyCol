package com.recycol.android.ui

import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.DeviceTier
import com.recycol.domain.model.InspectionRule
import com.recycol.domain.model.WasteMaterial
import com.recycol.domain.port.TierPreferenceRepository
import com.recycol.domain.usecase.AdjustPerformanceUseCase
import com.recycol.domain.usecase.ClassifyWasteUseCase
import com.recycol.domain.usecase.ManageHistoryUseCase
import com.recycol.domain.usecase.ResolveManualDisposalUseCase
import com.recycol.domain.usecase.ScanBinsUseCase
import com.recycol.domain.usecase.SelectCountryUseCase
import com.recycol.rules.DefaultRuleEngine
import com.recycol.testing.FakeBinAvailabilityRepository
import com.recycol.testing.FakeClassificationHistoryRepository
import com.recycol.testing.FakeBinDetector
import com.recycol.testing.FakeFrameQualityAnalyzer
import com.recycol.testing.FakeProfileRepository
import com.recycol.testing.FakeWasteClassifier
import com.recycol.testing.TestProfiles

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
        catalog = listOf(
            TestProfiles.threeBins.copy(
                isoCode = DEMO_ISO_CODE,
                inspectionRules = DEMO_INSPECTION_RULES,
            ),
        ),
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
            // El fake devolvía plástico, que no exige inspección y por tanto
            // dejaba fuera de la demostración el caso estrella del producto: el
            // vaso de café, que aparenta cartón reciclable pero se arruina si
            // lleva líquido dentro. Con esto la composición provisional recorre
            // el flujo entero —pregunta de suciedad incluida— en vez del atajo.
            classifier = FakeWasteClassifier(
                classification = ClassificationResult(
                    material = WasteMaterial.BEVERAGE_CARTON,
                    confidence = DEMO_CONFIDENCE,
                ),
            ),
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

/** Confianza de la clasificación de demostración. */
private const val DEMO_CONFIDENCE = 0.88f

/**
 * Materiales que exigen preguntar por la suciedad en la composición de demo.
 *
 * El perfil de prueba de `shared/testing` solo trae la regla del cartón para
 * bebidas, y el plan B aprobado cubre **toda la fibra**: papel y cartón, porque
 * la fibra absorbe la grasa y el líquido y se arruina, mientras que una botella
 * o una lata se enjuagan y se reciclan igual.
 *
 * Esto es composición de demostración, **no normativa**: las reglas de verdad
 * viven en `shared/resources/profiles/` y son ámbito de RULES, a quien
 * corresponde añadirlas a los perfiles reales (coordinación abierta). En cuanto
 * el catálogo real esté cableado, esta lista sobra.
 */
private val DEMO_INSPECTION_RULES = listOf(
    InspectionRule(
        material = WasteMaterial.BEVERAGE_CARTON,
        promptKey = "inspection.point_inside",
        requiresInteriorView = true,
    ),
    InspectionRule(
        material = WasteMaterial.CARDBOARD,
        promptKey = "inspection.show_box_interior",
        requiresInteriorView = true,
    ),
    InspectionRule(
        material = WasteMaterial.PAPER,
        promptKey = "inspection.point_inside",
        requiresInteriorView = false,
    ),
)
