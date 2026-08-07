# HANDOFF — Agente CORE (cierre 07/08/2026, ~07:15 UTC, orden de Juan)

Relevo del agente CORE. Léelo junto a `AGENTS.md` (operación del enjambre) y
`context-for-vibe-coding.md` (contexto y decisiones) — ambos en `main` y al día.

## Ámbito de CORE
`shared/domain/` y `shared/testing/` (contratos y fakes) · `gradle/libs.versions.toml`
y raíz de Gradle · `.github/workflows/` · `docker/` · más la guardia de fusión de
verdes con cierre de issues. Desde el 07/08 CORE **no** coordina agentes ni reporta
su estado: eso lo hace el orquestador directamente.

## Estado al cierre
- **En `main`, completos**: M0 (fundación+contratos, ampliado con #48/#49/#94),
  M1, M2, M3, M5, M7. M6 a falta de S35 (su PR #76 se cerró sin fusionar; issue #33
  abierta). M4 (ML) en curso — es el único trabajo de fondo y donde va todo el
  presupuesto. M8 arrancado (andamiaje #63 en main); M9 sin empezar (previsto).
- **Único cabo suelto de CORE: PR #132** (docs: hallazgo `trash` de Garbage v2 →
  regresión de ruta; criterio de optimización = **acierto de caneca, no top-1**).
  Verificado en verde por respaldo hospedado sobre su SHA exacto (`ddfc5939`) y con
  **auto-merge armado**: aterriza solo cuando un runner libre corra su check. Si al
  reiniciar sigue OPEN, basta re-disparar su check o el respaldo — **no rehacer**.
- **Cero issues propias abiertas; ramas `core/*` viejas borradas.** La única rama
  core viva además de #132 es `core/handoff` (este documento).

## Contratos vigentes (inmutables sin issue de coordinación)
- **Puertos** (`shared/domain/port/`): WasteClassifier, BinDetector,
  FrameQualityAnalyzer, DeviceTierPolicy, AuthProvider, ProfileRepository,
  BinAvailabilityRepository, ClassificationHistoryRepository, TierPreferenceRepository.
  `RuleEngine` en `shared/rules/` (impl real: `DefaultRuleEngine`).
- **Casos de uso** (`shared/domain/usecase/`): ClassifyWaste (flujo en dos pasos con
  inspección), ResolveManualDisposal, ScanBins, SelectCountry (resetea canecas al
  cambiar de país, #65), ManageHistory, AdjustPerformance, ConfidenceThresholds
  (inyectable; calibración = S39/QA).
- **Fakes deterministas** de todo en `shared/testing/` (`TestProfiles.threeBins`
  incluye el destino `special_collection`). Los destinos con ruta
  `SPECIAL_COLLECTION` están **exentos** de la restricción por canecas disponibles (#54).
- Perfiles: `shared/resources/profiles/` (schema + `co.json` con `special`,
  `unavailableBinNotice` y regla de inspección del cartón de bebidas).

## Trampas conocidas (no las repitas)
1. «Cierra #N» en español **no** auto-cierra issues: cerrar a mano al fusionar.
2. Stacks: la base que muestra `gh pr list` **miente** — verificar base viva con
   `gh pr view`; base = rama ya fusionada → retargetear a main antes de fusionar.
3. Prohibido `git add -A` y el clon compartido; worktree propio (`BotaBien-core`).
4. Email de commit: `200016968+JuanUrrego146@users.noreply.github.com`.
5. Runners: fallos «job failure sin paso fallido» = OOM contra `mem_limit`;
   «Could not read workspace metadata» = caché corrupta (secuela). Runbook completo
   en `.github/runner/README.md` (dimensionado nuevo de #131: 1×8 GB por defecto,
   2º runner solo con `--profile ola`). Respaldo: `gh workflow run ci-respaldo.yml --ref <rama>`.
6. **Disco C: al 98 %** — causa raíz de los crashes de la VM del 07/08. Hasta que
   Juan libere espacio, esperar fallos raros de runners/builds.
7. El estado real vive en ramas, drafts y `ml/reports/` — nunca reportar midiendo
   solo `main` e issues; preguntar al agente y usar el tablero #123.

## Herencias del relevo
- Revisar el diff de los 4 archivos `docker/` del PR #114 de ML cuando salga de
  draft (condición ya comunicada: versiones fijadas) — `docker/` es ámbito CORE.
- El riesgo legal de Garbage v2 (#77) tiene disparador antes de S28/comercialización.
- Targets iOS de `:shared` se activan en S43 (necesita macOS); protegido mientras
  tanto por `:shared:verifyPlatformIsolation`.
