# Handoff — Agente FRONT (M1: app shell, UI y design system)

Fecha de cierre: 07/08/2026 · Ámbito: `androidApp/src/main/kotlin/com/botabien/android/ui/` y `androidApp/src/main/res/`

## 1. Estado de M1: completo

Las seis sesiones del milestone están implementadas, fusionadas en `main` y con su issue cerrada.
**No queda ningún PR de FRONT por fusionar.**

| Sesión | Issue | PR | Estado |
|---|---|---|---|
| S04 Design system iOS | #3 | #59 | En `main` |
| S05 Navegación y onboarding de país | #4 | #67 | En `main` |
| S06 Pantalla de cámara con superposiciones | #5 | #75 | En `main` |
| S07 Detalle con justificación normativa | #6 | #79 | En `main` |
| S08 Ajustes (país, rendimiento, historial) | #7 | #110 | En `main` |
| S09 Accesibilidad e internacionalización | #8 | #110 | En `main` |

Trabajo adicional encargado por Juan fuera del plan original, también en `main`:

| Trabajo | PR |
|---|---|
| Selección manual y desambiguación de baja confianza (CUS-006) | #101 |
| Adopción de los casos de uso de la coordinación #94 (retirada de seams) | #113 |
| Baja confianza rediseñada como flujo protagonista | #127 |

> Nota sobre #106: contenía S08 y quedó **cerrado sin fusionar** cuando su rama base se borró al
> fusionarse la cadena «hacia dentro». Su contenido íntegro viajó en #110. Si se auditan PRs
> cerrados, no es trabajo perdido.

## 2. Mapa del código

```
androidApp/src/main/kotlin/com/botabien/android/ui/
├── theme/         BotaColors · BotaTypography · BotaSpacing · BotaShapes · BotaMotion · BotaTheme
├── components/    BotaButton · BotaCard · BotaBottomSheet · BotaActivityIndicator
│                  BotaStatusPill · BotaSelectionMark · BotaRouteGlyph
├── navigation/    AppDestination · AppNavState · AppNavHost   (pila propia, sin Navigation Compose)
├── country/       CountrySelectionScreen  (onboarding y cambio desde ajustes)
├── classify/      ClassifyScreen · ClassifyScreenState · HintPresenter (+prueba)
│                  CameraViewfinder · ManualSelectionSheet
├── result/        ResultDetailScreen
├── settings/      SettingsScreen
├── AppRoot.kt     grafo + arranque condicionado por el onboarding
└── AppDependencies.kt   composición de casos de uso (hoy sobre fakes)
```

Documentación viva del sistema de diseño: **`docs/design-system.md`** (tokens, componentes,
reglas de uso y notas de contraste). Léelo antes de tocar cualquier pantalla.

## 3. Decisiones de diseño tomadas (y por qué)

1. **Todo sale del design system (RNF-009).** Ningún archivo de UI declara `Color(...)`,
   `TextStyle(...)`, `RoundedCornerShape(...)` ni duraciones sueltas. Las pantallas leen
   `BotaTheme.colors/typography/spacing/shapes` y `BotaMotion`. `MaterialTheme` se configura por
   debajo solo para que los componentes internos de Material 3 hereden la apariencia; **las
   pantallas nunca leen `MaterialTheme.*`**.
2. **Sin ripple.** La respuesta táctil es la de iOS: escala 0.96 + atenuación con muelle sin
   rebote. Si alguien añade `indication = ripple()`, rompe la estética acordada.
3. **El color de caneca es dato, no diseño.** Sale de `BinDefinition.colorHex` del perfil y se
   convierte en color en tiempo de ejecución. El design system no conoce ningún color de caneca.
4. **Nunca solo color (RNF-010).** La caneca se comunica con color + nombre + `BotaRouteGlyph`
   (glifo geométrico por ruta). `secondaryLabel` claro se subió al 76 % de opacidad (iOS usa
   60 %) para cumplir AA como texto normal.
5. **Navegación propia en vez de Navigation Compose.** ~40 líneas (`AppNavState`) que dan control
   total de la transición de empuje iOS sin dependencia nueva. Si el grafo crece (deep links,
   restauración de estado), migrar es una issue propia — no lo hagas de paso.
6. **Indicaciones de captura discretas (RF-017/RF-018).** `HintPresenter`: una sola indicación a
   la vez, no se sustituye antes de 4 s, permanencia mínima de 1,5 s para no parpadear, y
   `POINT_INSIDE` entra inmediata por ser directiva del flujo, no sugerencia. Con prueba unitaria.
7. **Baja confianza como protagonista, no como excepción.** Ante la brecha del modelo con
   residuos reales (~91 % en foto limpia frente a ~42 % en residuo sucio), la app asume la duda en
   primera persona («Me cuesta identificarlo desde aquí») y **nunca culpa al usuario ni a su
   foto**. La hoja abre con hipótesis probables → lista completa a un toque → pregunta de
   contaminación si el material tiene regla de inspección; «Intentar otra toma» deja la cámara
   viva para que las indicaciones existentes guíen la siguiente. Mantén ese tono.
8. **La UI no decide canecas ni compone textos normativos.** Todo pasa por casos de uso de
   `shared/domain/usecase/` (invariante 4) y los textos normativos (nombre de caneca,
   justificación, aviso de caneca ausente) llegan renderizados desde el motor/perfil. La UI
   presenta; no interpreta.
9. **Cero literales visibles en código (RNF-011).** Todo texto en `values/strings.xml`, con
   `values-en/` completo. Los nombres de país se derivan del ISO con `Locale`.

## 4. Lo que tu relevo debe saber

### Pendientes reales (ninguno bloquea `main`)

- **#105 · Unificar la política de indicaciones (CAM↔FRONT).** `HintPresenter` (FRONT) y
  `CaptureHintEngine` (CAM, S13) implementan la misma política y divergen en tres puntos:
  intervalo (4 s vs 3 s), retiro (permanencia mínima vs inmediato) y —lo importante— **falta la
  supresión por confianza suficiente (RF-018) en el lado de FRONT**. CAM **se ofreció a hacer el
  cambio** (~30 líneas) dejando `HintPresenter` como envoltorio Compose que delega en el motor,
  con revisión de FRONT. **Acepta esa oferta**: es la vía correcta y ya está negociada. La
  decisión de producto pendiente es el intervalo (3 s o 4 s), a fijar por parámetro en un solo
  lugar junto con QA.
- **#126 · Hipótesis top-K en el contrato del clasificador.** `WasteClassifier` devuelve una
  única hipótesis; para ofrecer 2–3 candidatos en la desambiguación hace falta que EDGE emita el
  top-K que LiteRT ya calcula. **La UI ya está lista**: `ManualSelectionSheet` acepta
  `candidates: List<WasteMaterial>` y hoy se alimenta con la mejor hipótesis. Cuando el contrato
  lo ofrezca, solo cambia la línea que arma la lista en `ClassifyScreen` — ninguna pantalla.
- **Pregunta abierta a CAM en #75 (afecta más a EDGE que a FRONT).** El colector de
  `ClassifyScreenState` procesa dentro del `collect` conflado, pero llama a funciones `suspend`.
  Si «procesamiento síncrono» del contrato significa *sin puntos de suspensión*, el búfer del
  `FrameRing` podría reciclarse mientras LiteRT aún lee píxeles. Sin respuesta aún. Mi código
  funciona con cualquiera de las dos respuestas; **la que debe ajustarse si la respuesta es
  restrictiva es la ruta de inferencia**.

### Trampas y contexto operativo

- **Composición sobre fakes.** `AppDependencies.fakeAppDependencies()` cablea los casos de uso
  reales sobre los fakes de `shared/testing/` (y `DefaultRuleEngine` real). El módulo
  `:shared:testing` entra como `implementation` en el APK **a propósito y de forma temporal**;
  sale cuando DATA (S36/S37) y EDGE (S18) publiquen sus implementaciones y se cablee Koin. La
  persistencia real de país, historial y nivel de rendimiento **conmuta ahí, sin tocar pantallas**.
- **`InMemoryTierPreference`** en `AppDependencies` puede borrarse: `shared/testing` ya publica
  `FakeTierPreferenceRepository` (llegó con #96 después de que yo escribiera el doble local).
- **Un solo checkout compartido entre agentes.** Trabaja siempre en worktree propio
  (`git worktree add ../BotaBien-front <rama>`), **nunca `git add -A`** (arrastra trabajo ajeno
  sin confirmar), y `gradle/libs.versions.toml` es ámbito de CORE: entradas aditivas se toleran,
  el resto requiere issue.
- **Build:** no hay Android SDK en la máquina. `docker compose -p botabien-front run --rm
  android-build ./gradlew <tareas>`; el `-p` evita la contención del lock de Gradle con otros
  agentes. Docker Desktop se cae bajo carga («unexpected EOF»): reintenta antes de diagnosticar.
- **CI:** si Actions no crea runs de `pull_request` (pasó el 06/08, issue #111), fuerza el verde
  con `gh workflow run ci-respaldo.yml --ref <rama>` — la rama debe contener ese workflow, así que
  fusiona `main` primero.
- **Cuidado al apilar PRs.** Apilar ramas (`A ← B ← C`) provoca que, si QA fusiona «hacia
  dentro», los PRs intermedios queden huérfanos y se cierren sin fusionar (le pasó a CAM con
  S11–S14 y a mí con #106). **Abre cada PR contra `main`** en cuanto el anterior haya aterrizado.

### Qué NO hacer

- No mover lógica de negocio a pantallas o estados de UI: la caneca la decide `RuleEngine`.
- No condicionar nada por país con `if` en la UI: eso vive en el perfil JSON.
- No estilizar ad hoc: si falta un token, añádelo al design system y documéntalo.
- No persistir, cachear ni registrar frames de cámara (RNF-012).

---

Agente FRONT — 6 de 6 sesiones de M1 entregadas y en `main`.
