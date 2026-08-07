# Traspaso — agente DATA/AUTH (milestone M7)

Fecha: 07/08/2026 · Estado: **M7 cerrado**. Este documento existe para que un
relevo retome el ámbito de persistencia y autenticación sin releer el historial.

## Estado

Las tres issues del milestone «M7: Persistencia, historial y auth» están
implementadas, verificadas y fusionadas en `main`. El milestone no tiene issues
abiertas.

| Issue | Sesión | PR | Estado |
|---|---|---|---|
| #34 | S36 · Persistencia local con SQLDelight, DataStore y repositorios | #71 | fusionado |
| #35 | S37 · Historial local: registro, consulta y borrado | #72 | fusionado |
| #36 | S38 · Puerto de autenticación y modo invitado | #73 | fusionado |

Se desarrollaron como pila apilada y se fusionaron en ese mismo orden
(#71 → #72 → #73), porque cada PR tenía como base la rama del anterior. El
orden ya no importa para nadie: los tres están en `main`. Si alguna vez hay
que revertir, hágase en orden inverso (#73 → #72 → #71).

## Qué quedó construido

### Persistencia (S36)

- Base de datos SQLDelight `BotaBienDatabase`, paquete `com.botabien.data.db`,
  esquema en `shared/src/commonMain/sqldelight/`. Dos tablas: `available_bin`
  y `classification_record`.
- El driver es específico de plataforma y entra por el puerto
  `DatabaseDriverFactory`: Android usa `AndroidSqliteDriver`, las pruebas JVM
  usan el driver JDBC sobre archivo. Cuando llegue iOS (S43) solo hay que
  aportar el driver nativo.
- `PersistentProfileRepository` guarda la selección de país. El catálogo de
  perfiles no lo carga él: entra por `ProfileCatalogSource`, la costura
  acordada con RULES en la issue #48.
- `SqlDelightBinAvailabilityRepository` guarda el conjunto de canecas
  confirmadas. Guardar reemplaza la selección anterior de forma atómica y el
  conjunto vacío significa «sin restricción», tal como exige el contrato.
- Inyección de dependencias en `androidApp/di/DataModule.kt`, arrancada desde
  `BotaBienApplication`.

### Historial (S37)

`SqlDelightClassificationHistoryRepository` implementa el puerto: registra el
resultado, consulta de más reciente a más antiguo y borra de forma efectiva.
La confirmación de borrado ante el usuario es interacción de UI (S08, agente
FRONT); esta capa borra sin preguntar, como pide el contrato.

### Autenticación (S38)

`GuestAuthProvider` en `shared/src/commonMain/kotlin/com/botabien/data/auth/`.
`SignInUseCase` en `shared/domain/usecase/`. Pantalla `SignInScreen` y
`SignInViewModel` en `androidApp/ui/auth/`.

## Decisiones que conviene no revisitar

**El login de v1 es plomería, no una barrera.** No hay backend: la sesión
vigente es siempre `Session.Guest` y `signIn()` resuelve de forma determinista,
sin red, con `AuthUnavailableException`. La aplicación funciona completa sin
cuenta y ninguna función actual consulta la sesión. La pantalla existe y está
cableada al caso de uso para que conectar el backend en v2 no obligue a
rehacerla.

**El proveedor previsto para v2 es Supabase, y la capa no lo sabe.** No hay
ninguna dependencia de su SDK. Sustituirlo es registrar otro `AuthProvider` en
`authModule` (Koin): ni la UI ni el dominio cambian. Esto es lo que exige
RF-036 y es la razón de que el stub esté detrás del puerto y no en la pantalla.

**DataStore no puede vivir en `shared/`.** El invariante RNF-005 prohíbe
`androidx.*` en el módulo compartido y la tarea Gradle `verifyPlatformIsolation`
lo hace cumplir rompiendo el build. Por eso las preferencias entran por el
puerto `KeyValueStore` (dominio) con la implementación DataStore en
`androidApp/data/`. Si alguien intenta «simplificar» metiendo DataStore en
`shared`, el build lo rechaza, y con razón.

**El historial nunca guarda imágenes, y está probado.** RNF-012 no se sostiene
solo con la revisión de código: la prueba `ClassificationHistoryPersistenceTest`
verifica sobre el archivo `.db` real que el esquema no declara ninguna columna
`BLOB` y que, tras un uso completo, el archivo no contiene firmas JPEG ni PNG.
Si alguien añade una columna binaria al historial, esa prueba falla. Es
deliberado: no la debilites, cambia el diseño.

**El perfil activo degrada, no revienta.** Si la selección de país persistida
deja de existir en el catálogo (por ejemplo al retirar un perfil), el perfil
activo pasa a ser `null` y la app vuelve al onboarding en lugar de fallar.

## Lo que depende de otros agentes

- **RULES (S30)** debe implementar y registrar `ProfileCatalogSource` en el
  grafo de Koin. Hasta entonces, resolver `ProfileRepository` falla; como Koin
  resuelve de forma perezosa, registrar el módulo no rompe el arranque.
- **FRONT (S05)** debe integrar `SignInScreen` en la navegación. Hoy la
  pantalla existe pero no tiene ruta: el arranque no la muestra y la demo
  funciona como invitado sin ella. Esto fue deliberado para no colisionar con
  el ámbito de FRONT.
- La coordinación #94 la resolvió CORE en el PR #96: `ManageHistoryUseCase`
  consume el repositorio de historial sin cambios, y la preferencia de
  rendimiento (RF-031) quedó en el ámbito de EDGE.

## Notas de entorno para el relevo

- No hay JDK ni SDK de Android instalados en la máquina. Todo se compila en el
  contenedor del propio repositorio:
  `docker compose -p botabien run --rm android-build ./gradlew <tareas>`.
  La batería equivalente a CI es
  `:shared:allTests :shared:testing:allTests :shared:verifyPlatformIsolation :androidApp:testDebugUnitTest :androidApp:assembleDebug`.
- El repositorio lo comparten siete agentes. Trabaja en un worktree propio y
  añade archivos al índice de forma selectiva por ruta: nunca `git add -A`,
  porque el árbol suele tener cambios de otros agentes sin comitear.
- Si Gradle falla con «Could not read workspace metadata» tras un reinicio de
  Docker, la caché `kotlin-dsl` del volumen está corrupta: bórrala y repite.
- Sobre CI: el workflow de respaldo `ci-respaldo.yml` se lanza a mano
  (`gh workflow run ci-respaldo.yml --ref <rama>`) y sirve para verificar que
  una rama compila, pero su ejecución **no entra en el rollup de checks del
  PR**: la protección de rama solo la satisface el workflow `CI` disparado por
  el evento `pull_request`. Ante un fallo de infraestructura, lo que destraba
  es `gh run rerun --failed`. Queda abierta la issue #130 sobre un runner
  self-hosted con la caché de AGP dañada que falla
  `:androidApp:mergeDebugGlobalSynthetics` de forma intermitente.
