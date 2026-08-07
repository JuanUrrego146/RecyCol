# Traspaso del agente QA — 07/08/2026, 05:30

Estado al apagar la máquina. Lo escribe el agente QA (milestones M8/M9: pruebas,
banco de latencia, calidad del build y release) para quien lo releve.

## 1. Estado de la cola de PRs

`main` está **verde** y la protección de rama activa (check «Compilar y probar»
obligatorio, aplica también a administradores). En la campaña del 06–07/08 se
fusionaron **48 PRs**; la cola bajó de 26 a los que siguen abiertos:

| PR | Qué es | Estado real |
|---|---|---|
| #132 | docs: hallazgo Garbage v2 y criterio de acierto de caneca | Check **verde**, revisado, **auto-merge armado** |
| #133 | S35 · confirmación/edición/persistencia de canecas (reapertura) | Check **verde**, revisado, **auto-merge armado** |
| #134 | Perfil CO · regla de inspección de la caja de pizza (aprobada por Juan el 07/08) | Check **verde**, revisado, **auto-merge armado** |
| #114 | S22 · pipeline de ingesta de ML (+ cadena S23–S27) | Check en cola; auto-merge armado |
| #135 | Coordinación #21 · mancha de lente sintética (CAM→ML) | Llegó al final; **sin revisar por QA** |

«BLOCKED» en `gh pr list` con el check verde en el head es el **lag del
evaluador de merges de GitHub**, no un bloqueo real: el auto-merge dispara solo
cuando sincroniza. Si al reanudar siguen abiertos con verde, basta con
`gh pr merge <n> --merge`.

### Orden de fusión y cadenas

Los cinco abiertos apuntan **directo a `main`**: no hay cadenas vivas, se pueden
fusionar en cualquier orden. Regla que sigue vigente para las que vengan: **una
pila se aterriza de abajo hacia arriba**, y al fusionar la base hay que
reapuntar el siguiente eslabón con `gh pr edit <n> --base main` (si no, GitHub
lo deja huérfano y se pierde la cascada — pasó con M2/#98).

Antes de fusionar cualquier PR, dos comprobaciones no negociables:
1. **Check «Compilar y probar» en verde de verdad** (no cancelado, no en cola,
   no heredado de un commit anterior):
   `gh api repos/JuanUrrego146/BotaBien/commits/<sha>/check-runs`.
2. **Ámbito**: que los archivos pertenezcan al workstream del autor
   (`gh pr view <n> --json files`). Si toca ámbito ajeno sin issue de
   coordinación, se devuelve o se regulariza con una issue.

QA **no fusiona sus propios PRs** — los pasa a CORE.

## 2. Runners propios (CI)

Todo está en `.github/runner/README.md`; lo esencial:

- CI corre en **runners self-hosted dockerizados** sobre la misma imagen
  `botabien/android-build` que el build local: lo que verifica CI es idéntico a
  `docker compose run --rm android-build`.
- Configuración vigente: **un ejecutor, `botabien-runner-1`, con 8 GB**. El
  segundo está bajo el perfil compose `ola` (4 GB) y solo se levanta en picos:
  `docker compose -f docker-compose.runners.yml --profile ola up -d`.
- Levantar tras un reinicio de la máquina (no hace falta token si los volúmenes
  siguen): desde `.github/runner/`, `docker compose -f docker-compose.runners.yml up -d`.
  Comprobar: `gh api repos/JuanUrrego146/BotaBien/actions/runners --jq '.runners[].status'`.
- **Forzar un check sin esperar a GitHub** (vía de respaldo, hospedada):
  `gh workflow run ci-respaldo.yml --ref <rama>` — produce el mismo check
  «Compilar y probar», así que satisface la protección de rama igual.
- Cada run verde deja el **APK descargable** como artefacto:
  `gh run download <run-id> -n botabien-debug-apk`.

## 3. Lo que tu relevo debe saber

- **Diagnóstico que cuesta caro repetir**: un job en `failure` **sin ningún paso
  fallido** es OOM del contenedor contra su `mem_limit` (un pipeline en frío
  pide 6–7 GB). Comprobar con
  `docker inspect botabien-runner-1 --format '{{.State.OOMKilled}}'` antes de
  culpar a la caché o al código.
- **No relanzar checks en masa** (congeló la cola 90 min el 06/08) y **no
  empujar commits vacíos a ramas ajenas** para redisparar: mueve el head y
  orfaniza runs. Lo correcto es pedir el push al dueño o
  `gh api -X PUT repos/.../pulls/<n>/update-branch`.
- **Nunca trabajar en el clon compartido** `C:\Users\Juan\Documents\GitHub\BotaBien`:
  siete agentes lo comparten y cambia de rama debajo. Worktree propio de QA:
  `C:\Users\Juan\Documents\GitHub\BotaBien-qa`.
- **En esta máquina no hay JDK ni Android SDK**: toda verificación local va por
  Docker (`docker compose -p botabien-qa run --rm android-build ./gradlew ...`).
- El flujo **«Calidad»** (`.github/workflows/quality.yml`) añade pruebas de
  androidApp, Lint, invariantes por análisis estático
  (`.github/scripts/check_invariants.sh`) y validación del esquema de reportes
  de latencia. Si marca un invariante, leer el mensaje: distingue literales de
  país de comparaciones legítimas entre variables.

### Trabajo de QA pendiente (M8/M9)

- **S39 (#37)** umbrales de confianza, **S40 (#38)** integración extremo a
  extremo en dispositivo, **S41 (#39)** banco de latencia por gama,
  **S42 (#40)** privacidad y modo avión. Ninguna empezada.
- El **banco de latencia** tiene ya protocolo y esquema de reporte en
  `benchmark/` (formato común con EDGE); el módulo Macrobenchmark real se crea
  en S41, cuando existan los modelos de S27.
- Dos criterios que **quedaron deliberadamente sin cerrar** y hay que verificar
  antes de dar por hechas sus issues: **#18** (latencia por gama: no hay números
  reales todavía) y **#7** (los tres ajustes aún no persisten entre reinicios).
- Métricas que gobiernan la aceptación: **≥85 % top-1 de material** y **≥95 % de
  acierto de caneca** (esta manda), y la clasificación por cámara debe funcionar
  **en las tres gamas sin excepción**.
- Coordinaciones abiertas que QA levantó y siguen sin cerrar: frame con acceso a
  color para el detector de canecas (CAM↔BINS; sin él S34/S35 no funcionan en
  dispositivo real) y la convergencia de FRONT con los casos de uso de #96.
