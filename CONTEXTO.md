# CONTEXTO — BotaBien

**Este es el único documento que un agente necesita leer antes de trabajar.**
Sustituye a `context-for-vibe-coding.md`, a `ml/DATASETS.md` y a los siete
`HANDOFF-*.md` que cada agente escribió al cerrar sesión. Todo lo que sigue está
sintetizado aquí; lo que no está aquí, está enlazado desde la sección
[Documentos formales](#documentos-formales) y no se duplica.

Última consolidación: **07/08/2026**, tras el reinicio de la máquina y el cierre
de la campaña de siete agentes en paralelo del 06–07/08.

---

## 1. Qué es BotaBien

Aplicación móvil Android (portable a iOS) que, usando la cámara y redes
neuronales que corren **íntegramente en el dispositivo**, dice en qué caneca va
un residuo según la norma vigente del país activo. Detecta además si un
reciclable está contaminado y degrada la decisión cuando corresponde.

El problema real que resuelve: la gente no sabe a qué caneca va cada material, y
las reglas son más sutiles de lo que parecen. Un vaso de café aparenta cartón
reciclable, pero lleva recubrimiento de polietileno y suele tener líquido dentro:
en Colombia va a la **negra**, no a la blanca. La Resolución 2184 de 2019 exige
que lo aprovechable esté «limpio y seco», y esa condición es invisible desde
fuera del objeto.

> ### ⚠️ El proyecto es COMERCIAL, no académico
>
> Esto no es un detalle administrativo: **condiciona qué datasets y qué pesos
> preentrenados pueden entrar al pipeline.** Toda licencia NC (no comercial) o
> con cadena de derechos sin acreditar queda fuera, aunque mejore las métricas.
> El registro legal vigente es [`ml/DATA_LICENSES.md`](ml/DATA_LICENSES.md), que
> manda sobre cualquier otro documento. Cualquier texto que diga «proyecto
> académico sin fines comerciales» es material obsoleto anterior al 06/08/2026.

Todo ocurre sin conexión. No se envían imágenes a ningún servidor, no hay APIs
externas y la app funciona de gama baja a gama alta habilitando funciones de
forma escalonada.

**Alcance v1 (solo Android):** selección de país y perfil normativo ·
clasificación por cámara offline · escaneo de canecas disponibles · detección de
contaminación con toma dirigida · asistencia de captura · adaptación por gama ·
justificación normativa · historial local · pantalla de login preparada sin
backend.

**Fuera de v1:** IA en la nube, backend real, cámaras fijas, y **recolección de
dataset propio** (los modelos se entrenan solo sobre datasets públicos,
augmentación y síntesis — ver §7 para la excepción registrada).

---

## 2. Cómo se trabaja aquí — reglas del enjambre

El desarrollo lo ejecutan agentes de IA en paralelo. Juan Urrego dirige, revisa e
integra. Estas reglas existen porque **cada una se aprendió rompiendo algo**.

### Git y worktrees

- **Un worktree por agente.** El clon principal
  `C:\Users\Juan\Documents\GitHub\BotaBien` es **zona neutral compartida**: su
  rama cambia debajo de ti. Nunca trabajes ni commitees ahí.
  `git worktree add ../BotaBien-<agente> <rama>`.
- **Nunca `git add -A`.** Añade por rutas explícitas. Un `add -A` ya arrastró
  trabajo sin confirmar de otro agente.
- **Una rama por issue, un PR por issue**, patrón `<agente>/S<NN>-<slug>`.
  `main` no se toca directo (protección de rama activa, también para admins).
- **`Closes #N` en inglés.** «Cierra #N» **no** cierra la issue: GitHub lo
  ignora. Ya pasó con #14.
- **Abre cada PR contra `main`.** Apilar ramas (`A ← B ← C`) hace que, si se
  fusiona «hacia dentro», los PRs intermedios queden huérfanos y se cierren sin
  fusionar — le pasó a CAM (S11–S14), a FRONT (#106) y a BINS (#76). Si de todos
  modos apilas: se aterriza de abajo arriba y al fusionar la base hay que
  reapuntar el siguiente con `gh pr edit <n> --base main`.
- La base que muestra `gh pr list` **miente**: verifica con `gh pr view`.

### Build y CI

- **No hay JDK, Android SDK ni Python en la máquina.** Todo se compila en
  contenedor, y el sufijo `-p <proyecto>` es obligatorio para no chocar por el
  lock de Gradle:
  ```bash
  docker compose -p botabien-<agente> run --rm android-build ./gradlew <tareas>
  ```
  Batería equivalente a CI:
  `:shared:allTests :shared:testing:allTests :shared:verifyPlatformIsolation :androidApp:testDebugUnitTest :androidApp:assembleDebug`
- **CI verde obligatorio antes de fusionar.** El check que satisface la
  protección de rama es **«Compilar y probar»** (workflow `CI`). Verifícalo de
  verdad, no por el rollup:
  `gh api repos/JuanUrrego146/RecyCol/commits/<sha>/check-runs`.
- **Runners propios**, self-hosted y dockerizados, sobre la misma imagen
  `botabien/android-build` que el build local. Runbook en
  [`.github/runner/README.md`](.github/runner/README.md). Levantarlos tras un
  reinicio, desde `.github/runner/`:
  ```bash
  docker compose -f docker-compose.runners.yml up -d
  gh api repos/JuanUrrego146/RecyCol/actions/runners --jq '.runners[].status'
  ```
  Dimensionado vigente (#131): **1 ejecutor de 8 GB**; el segundo (4 GB) solo en
  picos, con `--profile ola`.
- **Respaldo hospedado** si Actions no crea runs de `pull_request` (pasó el
  06/08, issue #111): `gh workflow run ci-respaldo.yml --ref <rama>` — produce el
  mismo check y satisface la protección. La rama debe contener ese workflow, así
  que fusiona `main` primero.
- **No relanzar checks en masa** (congeló la cola 90 min) ni empujar commits
  vacíos a ramas ajenas para redisparar.
- Cada run verde deja el APK: `gh run download <run-id> -n botabien-debug-apk`.

### Diagnósticos que cuesta caro repetir

| Síntoma | Causa real |
|---|---|
| Job en `failure` **sin ningún paso fallido** | OOM del contenedor contra su `mem_limit` (un pipeline en frío pide 6–7 GB). Comprueba: `docker inspect botabien-runner-1 --format '{{.State.OOMKilled}}'` |
| «Could not read workspace metadata» | Caché `kotlin-dsl` del volumen corrupta tras reiniciar Docker: bórrala y repite |
| Run muerto con `runner: NONE`, cero pasos | Infraestructura: relanzar |
| `gh pr checks` vacío | Verifica por rama y SHA: `gh run list --branch <rama>` contra `git rev-parse origin/<rama>` |
| «BLOCKED» con el check verde en el head | Lag del evaluador de merges de GitHub, no un bloqueo real |
| Docker Desktop cae bajo carga («unexpected EOF») | Reintenta antes de diagnosticar |

### Coordinación

- **Ámbito de archivos exclusivo por agente** (§4). Tocar ámbito ajeno requiere
  **issue de coordinación explícita**. `CODEOWNERS` refleja la partición.
- **Tablero de estado del enjambre: issue #123.** Publica ahí cada hito en tres
  líneas: **qué** terminaste, **dónde** está (rama, PR, ruta de artefactos si no
  viven en git) y **qué sigue**. Computación larga: heartbeat cada ~30 min con
  ETA. Lo que no esté allí ni en una rama visible, no existe.
- **Sin respuesta no hay acuerdo.** Una oferta o petición **no es una asignación
  hasta que el receptor la acepta por escrito**. Quien pide lo dice
  explícitamente y con destinatario; quien recibe **responde siempre**, aunque
  sea «no» o «lo miro en N minutos». Ignorar una coordinación dirigida a ti dejó
  dos agentes parados dos horas con un entregable construido esperando (oferta
  de CAM a ML en #21). Una coordinación **sin respuesta en ~30 min es un
  atasco**: dilo en el tablero.
- En los reportes de estado distingue siempre **hecho** / **pedido y aceptado** /
  **pedido sin respuesta**. La última es la más importante: es donde se pierde
  trabajo.
- **Regla anti-parón**: nadie termina su turno con trabajo pendiente de su
  milestone. Al terminar un PR se arranca la siguiente issue en el mismo turno.
  Nadie espera CI, notificaciones ni fusiones.
- **QA fusiona** los PRs verdes y dentro de ámbito; **QA no fusiona los suyos**.
  **Juan autoriza** toda decisión de producto, de arquitectura que afecte a
  varios agentes, y de riesgo (legal, licencias, rendimiento). Históricamente
  CORE era el interlocutor de Juan; **desde el 07/08 el orquestador coordina a
  los agentes directamente** y CORE solo guarda los contratos compartidos.
- El estado real vive en **ramas, drafts y `ml/reports/`** — nunca lo reportes
  midiendo solo `main` e issues.
- Email de commit: `200016968+JuanUrrego146@users.noreply.github.com`. El Gmail
  personal hace que el push se rechace.

### Definición de «hecho»

Una issue se cierra cuando, **todo a la vez**:

1. Compila sin warnings nuevos y `./gradlew :shared:allTests` pasa.
2. Cumple literalmente el criterio de hecho escrito en la issue.
3. No rompe ningún invariante de arquitectura (§4).
4. No degrada ningún RNF medible; si toca la ruta de clasificación, se adjunta la
   latencia medida.
5. La lógica de dominio nueva tiene pruebas unitarias.
6. Los textos visibles están en recursos de cadenas.
7. El PR referencia la issue y los RF/CUS que implementa.

---

## 3. Estado real por milestone

Fecha de corte: **07/08/2026**, tras el reinicio.

| M | Workstream | Agente | Estado |
|---|---|---|---|
| **M0** | Fundación y contratos | CORE | ✅ **Cerrado en `main`** (ampliado con #48, #49, #94) |
| **M1** | App shell y design system | FRONT | ✅ **Cerrado en `main`** — 6/6 sesiones + extras (#101 selección manual, #113 adopción de casos de uso, #127 baja confianza como flujo protagonista) |
| **M2** | Cámara y calidad de imagen | CAM | ✅ **Cerrado en `main`**. Costuras abiertas: #104 (EDGE), #105 (FRONT), #21 (ML) |
| **M3** | Inferencia y gamas | EDGE | ✅ **Cerrado en `main`** — S15–S20 y la coordinación #102 fusionadas. Quedan 2 ramas listas sin PR: `edge/coord-94-tier-preference` y `edge/coord-s27-banco-validacion` |
| **M4** | Modelos y datos | ML | 🔶 **En curso — es el camino crítico y donde va todo el presupuesto.** Ver §7 |
| **M5** | Motor de reglas y perfiles | RULES | ✅ **Cerrado en `main`** — S29–S33 + coordinación #54 |
| **M6** | Escaneo de canecas | BINS | 🔶 S34 en `main`; **S35 en PR #133** (CI verde, sin fusionar). La issue #33 **no se cierra con #133**: falta la pantalla de confirmación, que es ámbito de FRONT |
| **M7** | Persistencia, historial y auth | DATA | ✅ **Cerrado en `main`** — S36, S37, S38 |
| **M8** | Confianza, integración y QA | QA | 🔶 Andamiaje en `main` (#63). **S39–S42 sin empezar** |
| **M9** | Preparación iOS y demo | RELEASE | ⬜ Sin empezar. Los targets iOS de `:shared` se activan en S43 (necesita macOS); protegido mientras tanto por `:shared:verifyPlatformIsolation` |

### PRs abiertos

| PR | Qué es | Estado |
|---|---|---|
| **#133** | S35 · confirmación, edición y persistencia de canecas | CI verde, revisado, **listo para fusionar** |
| **#135** | Coordinación #21 · mancha de lente sintética (CAM→ML) | **Sin revisar por QA** |
| **#114** | S22 · pipeline de ingesta de ML (+ cadena S23–S27) | **Draft.** Al salir de draft, CORE debe revisar el diff de los 4 archivos `docker/` (ámbito CORE, versiones fijadas) |
| #136–#140 | Handoffs de QA, DATA, RULES, CORE y FRONT | **Obsoletos: su contenido está en este documento.** Se cierran, no se fusionan; las ramas quedan en `origin` por si hay que recuperar algo |

Los abiertos apuntan directo a `main`: no hay cadenas vivas, se pueden fusionar
en cualquier orden.

### Salud de `main` tras el reinicio

- **Workflow `CI` («Compilar y probar»): verde** en el último merge completado
  (#134). El run de #132 quedó **en cola** porque los runners están caídos.
- **Workflow `Calidad`: rojo de forma sostenida.** Falla en
  `:androidApp:lintDebug` con **9 errores y 46 warnings de Android Lint**. No es
  infraestructura ni flake: es deuda real y lleva fallando desde al menos #110.
  No bloquea fusiones (no es el check obligatorio), pero está rojo.
- **Los dos runners self-hosted están `offline`** y **Docker Desktop no está
  arrancado**. Hasta levantarlos, ningún check de `CI` avanza y no se puede
  compilar ni entrenar nada.
- **Disco C: al 87 %** (32 GB libres). Mejoró respecto al 98 % que causó los
  crashes de la VM del 07/08, pero sigue siendo el margen que hay para datasets y
  checkpoints.

---

## 4. Arquitectura — dónde vive cada cosa

Kotlin Multiplatform. Un módulo `shared` que no conoce ninguna plataforma
(dominio, motor de reglas, perfiles, persistencia); cámara, inferencia e interfaz
son nativas y se conectan por puertos. Esa separación es lo que hace que iOS sea
una implementación de adaptadores y no una reescritura.

La cámara alimenta un pipeline de dos etapas: una red clasifica **material**, una
segunda inspecciona **contaminación**. El modelo nunca devuelve una caneca: la
traducción material → caneca la hace el motor de reglas contra un perfil
normativo intercambiable por país.

```
BotaBien/
├── shared/                          # KMP, SIN dependencias de Android
│   ├── domain/                      # entidades, casos de uso, puertos      → CORE
│   ├── testing/                     # fakes deterministas                   → CORE
│   ├── rules/                       # RuleEngine, perfiles, matcher canecas → RULES
│   ├── data/                        # SQLDelight, repositorios              → DATA
│   └── resources/profiles/          # perfiles por país en JSON             → RULES
├── androidApp/
│   ├── ui/                          # Compose, design system                → FRONT
│   ├── camera/                      # CameraX, calidad de imagen            → CAM
│   ├── inference/                   # LiteRT, delegados, gamas              → EDGE
│   │   └── bins/                    # detector de canecas                   → BINS
│   └── di/                          # Koin
├── ml/                              # pipeline Python de datos y modelos    → ML
├── benchmark/                       # banco de latencia                     → QA
├── .github/                         # workflows y runners                   → CORE
├── docker/                          # imágenes de build y GPU               → CORE
├── docs/  ·  plan/
└── iosApp/ · release/               # fase 2                                → RELEASE
```

### Invariantes — no se negocian

Un PR que los rompa se rechaza aunque funcione.

1. **`shared/` no conoce Android.** Cero imports de `android.*`, `androidx.*` o
   LiteRT. Toda capacidad de plataforma entra por un puerto de
   `shared/domain/port/`. Lo hace cumplir la tarea Gradle
   `:shared:verifyPlatformIsolation`, que rompe el build. Violarlo mata RNF-005.
2. **El modelo predice materiales, nunca canecas.** La conversión material →
   caneca ocurre **exclusivamente** en `RuleEngine`. Ni el clasificador, ni la
   UI, ni los repositorios deciden una caneca.
3. **Las reglas normativas viven en datos, no en código.** Todo lo específico de
   un país está en `shared/resources/profiles/<id>.json` + su entrada en
   `catalog.json`. **Prohibido `if (country == "CO")` en cualquier parte.**
4. **La UI nunca llama a `inference/` ni a `data/` directamente.** Siempre pasa
   por un caso de uso. Los `ViewModel` orquestan, no deciden.
5. **La gama del dispositivo se consulta, no se asume.** Toda función costosa
   pregunta a `DeviceTierPolicy`. Nadie asume NPU, GPU ni memoria.
6. **Las imágenes no salen del proceso.** No se escriben a disco, no se envían
   por red, no se registran en logs. El historial guarda el resultado, nunca el
   frame. Probado sobre el `.db` real: `ClassificationHistoryPersistenceTest`
   verifica que no hay columnas BLOB ni firmas JPEG/PNG en el archivo. Si alguien
   añade una columna binaria, esa prueba falla — es deliberado.
7. **Sin red en la ruta de clasificación.** La app entera funciona en modo avión.
8. **Ante la duda no se adivina**: material sin regla, o inspección exigida y no
   verificada → caneca conservadora del perfil, marcada con su `FallbackReason`.
9. La exactitud se reporta **siempre** sobre un dataset no visto en
   entrenamiento, y **siempre** incluye el acierto de ruta además del top-1.

### Contratos entre agentes — inmutables sin issue de coordinación

Puertos en `shared/domain/port/`: `WasteClassifier`, `BinDetector`,
`FrameQualityAnalyzer`, `DeviceTierPolicy`, `AuthProvider`, `ProfileRepository`,
`BinAvailabilityRepository`, `ClassificationHistoryRepository`,
`TierPreferenceRepository`. `RuleEngine` vive en `shared/rules/` (impl:
`DefaultRuleEngine`).

```kotlin
interface WasteClassifier {
    suspend fun classify(frame: ImageFrame): ClassificationResult
    suspend fun inspectContamination(frame: ImageFrame): ContaminationResult
}
interface BinDetector      { suspend fun detectBins(frame: ImageFrame): List<DetectedBin> }
interface FrameQualityAnalyzer { fun analyze(frame: ImageFrame): FrameQuality }
interface DeviceTierPolicy { val tier: DeviceTier; fun isEnabled(feature: Feature): Boolean }
interface RuleEngine {
    fun resolve(material: WasteMaterial, contamination: ContaminationState,
                availableBins: Set<BinId>, profile: CountryProfile): Disposal
}
interface AuthProvider {
    suspend fun currentSession(): Session          // v1 devuelve siempre Session.Guest
    suspend fun signIn(credentials: Credentials): Result<Session>
}
```

Casos de uso en `shared/domain/usecase/`: `ClassifyWaste` (dos pasos con
inspección), `ResolveManualDisposal`, `ScanBins`, `SelectCountry` (resetea
canecas al cambiar de país, #65), `ManageHistory`, `AdjustPerformance`,
`ConfidenceThresholds` (inyectable; su calibración es S39/QA).

Mientras una implementación real no exista, se trabaja contra el *fake*
determinista de `shared/testing/`. **Nadie espera a nadie.**

### Contrato de modelos EDGE ↔ ML — congelado

Los `.tflite` **no se versionan**: se reconstruyen con el pipeline de `ml/` y se
dejan caer en `androidApp/inference/src/main/assets/models/`. Mientras no
existan, se sirve un `StubWasteClassifier` determinista y la app funciona.

| Archivo | Modelo | Entrada | Salida |
|---|---|---|---|
| `material_low.tflite` | MobileNetV3-Small INT8 | 224×224×3 RGB UINT8 | softmax sobre materiales |
| `material_mid.tflite` | MobileNetV3-Large 0.75 INT8 | 224×224×3 RGB UINT8 | softmax sobre materiales |
| `material_high.tflite` | EfficientNet-Lite2 INT8 | 260×260×3 RGB UINT8 | softmax sobre materiales |
| `contamination.tflite` | binario INT8 | 224×224×3 RGB UINT8 | softmax `[CLEAN, CONTAMINATED]` |
| `detector.tflite` | detector genérico, **opcional** | 320×320×3 RGB UINT8 | `[cajas, clases, puntuaciones, conteo]` |

1. **Orden de salida** = orden de declaración de `WasteMaterial` (etapa 1) y
   `[CLEAN, CONTAMINATED]` (etapa 2), definido en `ModelOutputOrder`. Debe
   coincidir con `ml/taxonomy/label_mapping.yaml`.
2. **Layout de entrada**: `[1, lado, lado, 3]`, RGB por filas, sin alfa, UINT8
   `[0,255]`. Una entrada FLOAT32 se declara en su `ModelSpec` con media y
   desviación.
3. Si el número de clases no cuadra con la taxonomía, el clasificador **falla con
   error explícito**; nunca mapea en silencio.
4. Cambiar nombres, tamaños u orden de clases **es cambiar el contrato**.

Banco de validación autoservicio para ML (rama
`edge/coord-s27-banco-validacion`, un solo comando):
`./androidApp/inference/validate_models.sh ml/dist/models <conjunto-eval>` —
verifica contrato, orden de clases contra el lote de referencia, **pérdida por
cuantización INT8 vs float** y latencia/memoria. Detalle en
[`androidApp/inference/README.md`](androidApp/inference/README.md).

### Política de gama

`DeviceTierPolicy` combina capacidades declaradas (techo) con un
**micro-benchmark de latencia real** (manda dentro de ese techo), con presupuesto
duro de 1,2 s para no comprometer el arranque. Cachea en `SharedPreferences`
invalidadas por fingerprint del sistema. Degrada un escalón si la latencia
observada se sostiene sobre el umbral; el ajuste manual del usuario (RF-031)
manda sobre todo y suspende la degradación automática.

| | Gama baja | Gama media | Gama alta |
|---|---|---|---|
| Clasificación por cámara | **Sí**, bajo demanda con botón | **Sí**, continua ~5 fps | **Sí**, continua ~10 fps |
| Detección del objeto | No: marco guía fijo | Detector ligero | Detector completo |
| Etapa de contaminación | Solo en captura manual dirigida | Bajo demanda de la regla | Automática |
| Escaneo de canecas | Foto única | Continuo | Continuo |
| Análisis de calidad | Nitidez y luz | Completo | Completo |

**La clasificación por cámara funciona en las tres gamas sin excepción.** Lo que
se degrada es la fluidez y las funciones auxiliares, nunca la función principal.

---

## 5. Stack y convenciones

- Kotlin 2.x, KMP, JDK 17, Gradle con version catalog en
  `gradle/libs.versions.toml` (**ámbito CORE**: entradas aditivas se toleran, el
  resto requiere issue).
- Android: Compose + Material 3, CameraX, LiteRT (`com.google.ai.edge.litert`),
  `minSdk = 26`, `targetSdk = 34`. Koin para DI, corrutinas y `Flow` (nada de
  `GlobalScope`), kotlinx.serialization, SQLDelight + DataStore.
- Entrenamiento: Python 3.11, PyTorch → LiteRT. Vive en `ml/`, aislado de la app.
- **Identificadores en inglés** (`WasteCategory`, `classifyWaste`); `camelCase`,
  `PascalCase`, `SCREAMING_SNAKE_CASE`. **KDoc, comentarios, textos de UI y
  mensajes de commit en español.**
- Clean Architecture por capas, orientación a objetos, dominio puro.
- No introducir dependencias nuevas sin justificarlo en el PR y añadirlas al
  version catalog.

### RNF siempre vigentes

| RNF | Qué exige |
|---|---|
| **RNF-001** | ≤ 2 s extremo a extremo en gama media, ≤ 4 s en baja. **Meta de diseño, no bloqueo**: si un dispositivo no llega, degrada funciones pero la clasificación sigue |
| **RNF-002** | Funcionamiento sin conexión, siempre |
| **RNF-004** | Un país nuevo = un archivo JSON. Si exige tocar Kotlin, el diseño está mal |
| **RNF-005** | `shared/` compila para JVM e iOS sin cambios |
| **RNF-008** | ≥ 85 % top-1 de material y **≥ 95 % de acierto de caneca**. **La segunda manda** |
| **RNF-009** | Estética minimalista iOS; todo componente sale del design system |
| **RNF-011** | Cero literales de texto visible en código |
| **RNF-012** | Los frames de cámara no se persisten ni se registran |

### Qué NO hacer

- Añadir funcionalidad no trazada a un RF. Si falta un requerimiento, **abre una
  issue nueva**; no lo improvises dentro de otra.
- Cambiar stack, estructura de módulos o convenciones de nombres.
- Mover lógica de negocio a la UI o a los `ViewModel`.
- Introducir red en la ruta de clasificación, «ni temporalmente para probar».
- Condicionar comportamiento por país con `if` en código.
- Guardar, cachear o loguear frames.
- Modificar los contratos sin issue dedicada.
- Implementar el backend de autenticación en v1: solo el stub y su interfaz.
- Reportar exactitud medida sobre el mismo dataset con el que se entrenó.
- Inventar personas, roles ni responsables en la documentación.

### Decisiones de diseño ya tomadas — no revisitar

- **UI**: nada de `ripple` (respuesta iOS: escala 0.96 + muelle sin rebote).
  Navegación propia (`AppNavState`, ~40 líneas) en vez de Navigation Compose. El
  color de caneca es **dato** (`BinDefinition.colorHex`), no diseño: el design
  system no conoce ningún color de caneca. **Nunca solo color** (RNF-010): color
  + nombre + `BotaRouteGlyph`. Tokens y componentes en
  [`docs/design-system.md`](docs/design-system.md) — léelo antes de tocar una
  pantalla.
- **Login v1 es plomería, no barrera.** Siempre `Session.Guest`, sin red,
  `AuthUnavailableException` determinista. El proveedor previsto para v2 es
  Supabase y **la capa no lo sabe**: sustituirlo es registrar otro `AuthProvider`
  en Koin.
- **DataStore no puede vivir en `shared/`** (RNF-005): entra por el puerto
  `KeyValueStore` con implementación en `androidApp/data/`.
- **El perfil activo degrada, no revienta**: si el país persistido desaparece del
  catálogo, el perfil pasa a `null` y la app vuelve al onboarding.
- **Composición temporal sobre fakes**: `AppDependencies.fakeAppDependencies()`
  cablea los casos de uso reales sobre `shared/testing/` y `:shared:testing` entra
  en el APK **a propósito y de forma temporal**. Sale cuando DATA (S36/S37) y
  EDGE (S18) se cableen por Koin, **sin tocar pantallas**.
- **Trampas de Kotlin/Gradle**: prohibido `vararg` de value classes (`BinId`);
  fusionar `main` puede romper `libs.versions.toml` **sin conflicto textual**
  (verifica `git diff <rama> origin/main -- gradle/libs.versions.toml`); al
  añadir una caneca a un perfil hay pruebas que afirman su número
  (`ColombiaProfileTest`, `ProfileResourcesTest`).

---

## 6. Decisiones de producto de Juan

Están tomadas. No se re-discuten sin él.

1. **`ELECTRONIC` entra en v1 con ruta a punto de recolección especial**, igual
   que las pilas. **No hay detección automática**: se llega por selección manual
   o por desambiguación de baja confianza (CUS-006). Motivo: ninguno de los
   datasets del inventario tiene clase de aparatos eléctricos (issue #54).
2. **Frase aprobada del aviso de caneca ausente:**
   > «No hay {ideal} disponible; usa {assigned}.»

   Es plantilla del perfil (`unavailableBinNotice`) que el motor renderiza con
   los nombres visibles — no un literal en código.
3. **Regla de inspección del vaso de café**: el cartón para bebidas exige vista
   interior. Limpio → caneca blanca; contaminado → negra. Es dato del perfil
   (`contaminatedFallback` en `MaterialRule`), no lógica del clasificador.
4. **La caja de pizza exige inspección del interior igual que el vaso de café**
   (aprobado el 07/08, PR #134, ya en `main`). Clave de recurso pendiente en
   FRONT: `inspection.show_box_interior`.
5. **Perfiles normativos por país + institución.** El catálogo modela
   **país → institución**, no solo país. Hoy existen `co.json` (Resolución 2184
   de 2019, tres canecas), `co-gtc24.json` (GTC 24) y `es.json`. S33 lo demostró:
   el commit que añade España y GTC 24 **no toca una línea de Kotlin**.
   *Limitación conocida*: `ProfileRepository.setActiveProfile(isoCode)` no puede
   activar variantes institucionales porque `co` y `co-gtc24` comparten
   `isoCode`. Propuesta registrada en #48 (seleccionar por *id de perfil*). No
   bloquea la v1, que usa los perfiles por defecto.
6. **Los destinos `SPECIAL_COLLECTION` están exentos** de la restricción por
   canecas disponibles (un punto posconsumo no es una caneca del entorno) y **se
   excluyen del escaneo**: ni se detectan por color, ni se añaden a mano, ni
   entran al «omitir escaneo».
7. **Baja confianza es el flujo protagonista, no la excepción.** Ante la brecha
   del modelo con residuos reales, la app asume la duda en primera persona («Me
   cuesta identificarlo desde aquí») y **nunca culpa al usuario ni a su foto**.
   Mantén ese tono.
8. **Garbage v2 se usa pese al riesgo legal** (decisión del 06/08) para no frenar
   el camino crítico — pero **bloquea el lanzamiento comercial** hasta revisión
   legal. Ver §8.

---

## 7. ML — estado y hallazgos (lo más valioso del proyecto)

M4 es el **camino crítico**. Los artefactos pesados (`ml/data/` 2,0 GB,
`ml/runs/` 94 MB, `ml/reports/`) **no están en git por diseño**: viven en el
worktree `BotaBien-ml` y **no se pueden regenerar sin volver a descargar y
entrenar**.

### Pool de datos

Partición determinista con semilla fija, deduplicación perceptual (pHash) sobre
la unión de fuentes de entrenamiento antes de particionar:

> **train 17 176 · val 3 088 · control RealWaste 4 752**

| Dataset | Rol | Nota |
|---|---|---|
| **Garbage Dataset v2** (~13,3k) | Entrenamiento, **columna vertebral** (~70 % del pool comercial) | Riesgo legal abierto (§8) |
| **TrashNet** (2 527) | Entrenamiento, complemento | MIT. Fondo blanco uniforme, lejos del dominio móvil |
| **TACO** (~1 500 img / 4 784 anotaciones) | Entrenamiento vía recortes de bbox | CC BY 4.0 (paquete Zenodo). **Única fuente de `BEVERAGE_CARTON`** |
| **RealWaste** (4 752) | 🔒 **CONTROL — nunca entrena** | CC BY 4.0. Residuos reales degradados fotografiados en un relleno sanitario |
| Clothing Dataset (CC0), Fresh & Rotten Fruits (CC BY), Open Images V7 (filtrado) | Refuerzo de clases débiles | `TEXTILE`, `ORGANIC`, `METAL`, `BEVERAGE_CARTON` |
| ~~Garbage Classification 12c~~ · ~~ZeroWaste~~ | ❌ **Excluidos** | Imágenes © autores originales / CC BY-**NC**. Incompatibles con uso comercial |

Ningún dataset se mezcla sin pasar por `ml/taxonomy/label_mapping.yaml`. Toda
fuente se registra en [`ml/DATA_LICENSES.md`](ml/DATA_LICENSES.md) **antes** de
usarse.

### Métricas contra control — el hallazgo central de M4

| Run | val material | val ruta | **control material** | **control ruta** |
|---|---|---|---|---|
| baseline `low` (sin v2) | 88,9 % | 98,4 % | 39,3 % | 65,9 % |
| `full-v2` (con v2) | 91,0 % | 97,7 % | 42,1 % | **61,4 %** |

**La val interna mejora y el control empeora.** Esa divergencia es el hallazgo
central: 98,4 % de ruta en val frente a 65,9 % contra control. **Cualquier
decisión tomada solo con val interna es sospechosa por defecto.**

**RNF-008 (≥85 % material, ≥95 % ruta) no se cumple hoy.**

Barrido de arquitectura/lr en `ml/runs/sweep_summary.md`; ganador provisional
**en val interna** (por tanto, provisional de verdad): EfficientNet-B2.

### Causa raíz: la carpeta `trash` de Garbage v2 está envenenada

Auditoría cruzada de RULES sobre `label_mapping.yaml` y las matrices colapsadas a
canecas con el perfil colombiano (issue #23). El mapeo **no tiene errores**:
`trash → RESIDUAL` es **fiel a la etiqueta de origen y aun así dañino**.

Esa carpeta está llena de **envases sucios y deformados** — visualmente
PLASTIC/CARDBOARD/GLASS, o sea **caneca blanca** — etiquetados RESIDUAL, o sea
**negra**. El modelo aprende «envase degradado ⇒ RESIDUAL». Y como el control es
íntegramente dominio degradado, dispara reciclables a la caneca negra.

Masa de error en el control, colapsada a canecas:

| | baseline | full-v2 | Δ |
|---|---|---|---|
| **blanca → negra (caro)** | 447 | **866** | **+419** |
| blanca acertada | 2 637 | 2 131 | −506 |
| verde acertada | 124 | 270 | +146 |
| negra acertada | 372 | 503 | +131 |

v2 compró +17 pp de ORGANIC y +15 pp de RESIDUAL pagando entre 24 % y 35 % de
cada corriente blanca hacia la negra. **Eso explica íntegro el −4,5 pp de ruta.**
Errores totales −134, pero **errores caros +229** (1 619 → 1 848).

Se excluye de forma **declarativa**, sin tocar el manifiesto de S22 (para no
romper la reproducibilidad de la partición):
`--exclude garbage_dataset_v2:RESIDUAL` (350 filas en train, 51 en val).

### Optimizar RUTA DE CANECA contra control, no top-1

**El 44 % de los errores de material son gratis.** PLASTIC ↔ METAL ↔ GLASS ↔
PAPER ↔ CARDBOARD caen todos en la caneca blanca; TEXTILE ↔ RESIDUAL, todos en la
negra. Por eso convivían top-1 39,3 % y ruta 65,9 %: **el modelo era mejor de lo
que su top-1 sugería.**

Lo que el usuario sufre son las **confusiones caras**: cualquier cosa ↔ ORGANIC,
el cruce blanca ↔ negra, y `BEVERAGE_CARTON` confundido con `CARDBOARD` (se salta
la inspección del vaso: la contaminación no se detecta y termina en blanca).

De ahí las dos palancas, **ninguna ejecutada todavía ni una sola vez** — el
primer run que las use debe mirarse con ojo crítico:

- `--route-cost` — pérdida sensible a coste de caneca (confusión intra-blanca
  ≈ 0, cruce blanca ↔ negra/verde alto).
- `--select route-macro` — checkpoint por ruta **promediada por clase**. En micro,
  TEXTILE y ORGANIC deciden solos y tapan el hundimiento de una clase entera.

**Publicar siempre la matriz colapsada por caneca** junto a la de material.
`evaluate_control.py` ya la emite. Sin ella las decisiones se toman a ciegas.

### 🔒 El control no se toca. Nunca.

RealWaste **no entrena, no ajusta umbrales y no elige checkpoints**. Es la única
evidencia de generalización que queda; en cuanto se use para seleccionar algo,
deja de serlo. Toda selección se hace sobre val interna.

### Qué falta en M4, en orden

1. **Reentrenar sin la carpeta `trash` y evaluar contra control.** Prioridad 1:
   demuestra o refuta el diagnóstico. **Criterio de éxito**: la ruta contra
   control recupera **≥ 65 %** conservando la mejora de ORGANIC que trajo v2
   (31,9 % frente al 14,6 % del baseline).
2. Variantes `mid` y `high` con la receta final.
3. **S26 contaminación: se perdió en un crash de Docker, hay que relanzarlo.**
   Los pares sintéticos sí existen (`ml/data/derived/contamination/pairs.csv`).
4. S27 export INT8 con pérdida de exactitud **medida** (banco de EDGE, §4).
5. S28 `ml/REPORTE_METRICAS.md` (`eval/build_report.py` ya está escrito).

Coste aproximado en la RTX 3060 Ti: `low` ~50 min, `mid` ~37 min, `high` ~41 min
por run, más unos minutos por evaluación de control.

### Lección del orquestador que se cayó

`m4_final.ps1` encadenaba reentrenamiento → evaluación → export → reporte sin
intervención, y **perdió ocho horas de GPU sin entrenar nada**. Docker Desktop se
cayó; `docker ps` empezó a devolver **error** en vez de una lista, y una salida
vacía por error es indistinguible de «no hay contenedores». El bucle de espera
interpretó «GPU libre» y siguió. Con `$ErrorActionPreference = "Continue"` y **sin
comprobar un solo exit code**, los diez pasos siguientes fallaron al instante
contra el daemon muerto: la cadena se «completó» en 5 segundos y escribió
`M4-FINAL-COMPLETO` en el log. Los errores reales solo estaban en el `.err.log`,
que nadie miraba.

**El orquestador nuevo debe comprobar el exit code de cada paso, distinguir
«Docker caído» de «GPU libre», y escribir FALLO en el log que sí se vigila.**

### Entorno de ML

**Docker es el único entorno.** No hay Python local.
`docker compose -p botabien-ml` (CPU) o el overlay
`docker-compose.gpu.yml -p botabien-ml-gpu`. `shm_size` está acotado a 2 GB **a
propósito**: subirlo invita al OOM killer de la VM de WSL2, que ya tumbó el
barrido una vez. Los runners de CI y los entrenamientos comparten la RAM de esa
VM (issue #128): no los corras a la vez.

### Sin usar todavía

- `frame_quality_gate.py` de CAM, para caracterizar la degradación del control y
  ver si el filtro de calidad rescataría parte de la brecha.
- El banco de validación de EDGE, para separar **pérdida de cuantización** de
  **pérdida de dominio** (S27, issue #25). Con una brecha de 91 % → 42 %, esa
  separación es la información que decide si el problema es el INT8 o el dataset.

---

## 8. Riesgos abiertos

| Riesgo | Estado |
|---|---|
| 🔴 **LEGAL — Garbage Dataset v2 sin cadena de derechos acreditada** (issue **#77**) | **Bloquea el lanzamiento comercial, no el desarrollo.** La ficha de Kaggle dice MIT y el paper dice CC BY 4.0 — ambas permiten uso comercial, pero la inconsistencia ya es señal de gestión informal, y parte del contenido viene de «repositorios públicos y web scraping curados»: la declaración del autor solo vale para lo que era suyo. **Ningún modelo entrenado con él puede publicarse comercialmente sin revisión legal previa.** Aporta ~70 % del pool: excluirlo sin reemplazo hace inalcanzable RNF-008 |
| 🟠 **Salida al riesgo legal: dataset propio** | Registrada, no decidida. Completo (11 clases, 5 500–11 000 fotos): 25–90 h de captura + 15–20 h de QC. **Quirúrgico (solo `BEVERAGE_CARTON`, 300–500 fotos): 3–5 h** — máximo retorno por hora, recomendado hacerlo pronto porque ninguna fuente pública apta cubre bien esa clase |
| 🔴 **El caso estrella no es verificable** | RealWaste **no contiene `BEVERAGE_CARTON`, `BATTERY` ni `ELECTRONIC`**. Hoy el diferenciador del producto — el vaso de café contaminado — no tiene control de dominio real. RULES dejó especificado un mini-set propio de ≈400 fotos, **de evaluación exclusivamente, jamás de entrenamiento** |
| 🟠 **La contaminación sintética puede no transferir** | Se entrenó solo con síntesis; la transferencia a suciedad real solo tiene control indirecto. Plan B: reducir la etapa 2 a una pregunta explícita al usuario, conservando el flujo de UX |
| 🟠 **Los datasets públicos no generalizan al móvil real** | Ya materializado: 91 % en dominio propio vs 42 % contra control. Mitigación en curso: validación cruzada obligatoria, augmentación agresiva de S23, exclusión de `trash` |
| 🟠 **Pesos preentrenados** | «APTO con nota»: las licencias de distribución (BSD-3/Apache 2.0) permiten uso comercial, pero hay debate jurídico no resuelto sobre pesos entrenados sobre datasets solo-investigación (ImageNet, DUTS-TR). Práctica de industria: utilizables. **La aceptación es decisión de Juan** |
| 🟡 **`Calidad` rojo en `main`** | 9 errores de Android Lint sin atender. No bloquea fusiones pero es deuda visible |
| 🟡 **Costura CAM ↔ EDGE (#104)** | **Bloquea la integración real.** CameraX entrega `LumaImageFrame` (solo luma); el clasificador exige `PixelAccessFrame` con `readArgbPixels()` / `readArgbRegion()`. Sin esa conversión YUV→ARGB, la clasificación no puede consumir el flujo de cámara. Lo mismo bloquea al detector de canecas (#108) |
| 🟡 **Infraestructura** | Disco C: al 87 %; RAM de la VM de WSL2 compartida entre runners y GPU (#128); caché de AGP corrupta en un runner (#130) |

### Otras coordinaciones abiertas

- **#105 · CAM ↔ FRONT** — `HintPresenter` (FRONT) y `CaptureHintEngine` (CAM)
  implementan la misma política y divergen: intervalo (4 s vs 3 s), retiro
  (permanencia mínima vs inmediato) y —lo importante— **falta la supresión por
  confianza suficiente (RF-018) en FRONT**. CAM se ofreció a hacer el cambio
  (~30 líneas) dejando `HintPresenter` como envoltorio que delega en el motor.
  **La oferta está negociada: acéptala.** Decisión de producto pendiente: 3 s o
  4 s, a fijar por parámetro en un solo lugar junto con QA.
- **#126 · Top-K en el contrato del clasificador** — `WasteClassifier` devuelve
  una única hipótesis; la desambiguación necesita 2–3 candidatos. **La UI ya está
  lista** (`ManualSelectionSheet` acepta `candidates: List<WasteMaterial>`):
  cuando EDGE emita el top-K que LiteRT ya calcula, solo cambia la línea que arma
  la lista.
- **#100 · `ScanBinsUseCase`** — debería excluir los destinos
  `SPECIAL_COLLECTION` del emparejamiento por color, como ya hacen el matcher y
  `BinSelection`.
- **#107** — colocación del matcher de color en `shared/rules/bins`.
- **#33** — pantalla de confirmación de canecas (FRONT). La lógica está lista.
- **Criterios sin cerrar que QA debe verificar**: **#18** (latencia por gama: no
  hay números reales) y **#7** (los tres ajustes aún no persisten entre
  reinicios).

---

## 9. Carpetas en disco

Tras la limpieza del 07/08, en `C:\Users\Juan\Documents\GitHub\`:

| Carpeta | Qué es | Por qué se conserva |
|---|---|---|
| `BotaBien` | **Clon principal**, en `main` y limpio. Zona neutral compartida | Es el repositorio de referencia y el padre de todos los worktrees. **No se trabaja ni se commitea aquí** |
| `BotaBien-ml` | Worktree del agente ML (`ml/S22-pipeline-ingesta`) | **Contiene los datasets (2,0 GB), los checkpoints (94 MB) y los reportes de entrenamiento, que no están en git y no se regeneran sin volver a descargar y entrenar.** Nunca borrar sin respaldar `ml/data/`, `ml/runs/` y `ml/reports/` |
| `BotaBien-org` | Worktree temporal de esta consolidación | Se elimina en cuanto se fusione el PR de `CONTEXTO.md` |

Se eliminaron **16 worktrees** de agentes cerrados (`BotaBien-core`,
`BotaBien-front`, `BotaBien-cam`, `BotaBien-data`, `BotaBien-qa`,
`BotaBien-qa-hotfix`, `BotaBien.worktrees/rules` y los nueve
`.botabien-worktrees/edge-*`). Todos estaban limpios y con sus commits
empujados; **sus ramas siguen en `origin`**, así que no se perdió nada.

El clon principal estaba en un `HEAD` desprendido de un commit viejo con
cambios sin commitear. Se sincronizó con `main`; su estado anterior quedó en
`stash@{0}` por si acaso (`git stash list` / `git stash pop`). Lo único que
había allí y en ningún otro sitio —el bloque de `.gitignore` para las
credenciales de Kaggle— se rescató y va en este mismo commit.

**Un agente nuevo crea el suyo y lo borra al terminar:**

```bash
git -C C:/Users/Juan/Documents/GitHub/BotaBien worktree add ../BotaBien-<agente> -b <agente>/S<NN>-<slug> origin/main
# ... al cerrar, con todo empujado:
git -C C:/Users/Juan/Documents/GitHub/BotaBien worktree remove ../BotaBien-<agente>
```

---

## Documentos formales

Sobreviven porque son entregables o registros, no contexto. **Enlázalos, no
copies su contenido aquí.**

| Documento | Qué contiene |
|---|---|
| [`docs/F_Analisis_de_Requerimientos_V1,0_BotaBien.md`](docs/F_Analisis_de_Requerimientos_V1%2C0_BotaBien.md) · [.docx](docs/F_Analisis_de_Requerimientos_V1%2C0_BotaBien.docx) | **Especificación completa**: CUS, RF, RNF y matriz de trazabilidad. Es la fuente de verdad de qué hay que construir |
| [`ml/DATA_LICENSES.md`](ml/DATA_LICENSES.md) | **Registro legal de procedencia**: licencia, evidencia y veredicto comercial de cada dataset, peso y herramienta. Manda sobre el resto |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Diagramas Mermaid: contexto, casos de uso, modelo de dominio, secuencias, estados, y la tabla de decisiones de arquitectura con sus alternativas descartadas |
| [`plan/plan_de_trabajo.md`](plan/plan_de_trabajo.md) | Estimación, cronograma y criterio de hecho por sesión (S01–S44) |
| [`docs/design-system.md`](docs/design-system.md) | Tokens, componentes y notas de contraste. Léelo antes de tocar una pantalla |
| [`.github/runner/README.md`](.github/runner/README.md) | Runbook de los runners self-hosted |
| [`androidApp/inference/README.md`](androidApp/inference/README.md) | Banco de validación de `.tflite` y contrato de modelos, en detalle |
| [`benchmark/README.md`](benchmark/README.md) | Protocolo y esquema del reporte de latencia |
| [`shared/resources/profiles/README.md`](shared/resources/profiles/README.md) | Esquema del perfil normativo y cómo añadir un país |
| [`ml/README.md`](ml/README.md) | Estructura del pipeline de ML, fuentes y comandos de descarga (`docker/GPU.md` llega con el PR #114) |
| [`README.md`](README.md) | Portada pública del repositorio |
