#!/usr/bin/env bash
# Crea el repositorio privado BotaBien en GitHub con labels, milestones e issues.
# Requisitos: gh instalado y autenticado (gh auth login), git configurado.
# Uso: cd BotaBien && bash setup_repo.sh
set -euo pipefail

REPO_NAME="BotaBien"
DESC="Clasificacion de residuos por camara con redes neuronales en el dispositivo, segun la norma de cada pais"

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: GitHub CLI (gh) no esta instalado."; exit 1
fi
gh auth status >/dev/null 2>&1 || { echo "ERROR: ejecuta primero: gh auth login"; exit 1; }
cd "$(dirname "$0")"
[ -f "README.md" ] || { echo "ERROR: ejecuta el script desde dentro de la carpeta BotaBien."; exit 1; }

echo "==> 1/5 Creando repositorio privado"
git init -b main 2>/dev/null || true
git add -A
git commit -m "docs: especificacion inicial, arquitectura y plan de trabajo" 2>/dev/null || true
gh repo create "$REPO_NAME" --private --source=. --description "$DESC" --push \
  || { echo "El repositorio ya existe; haciendo push."; git push -u origin main; }

OWNER=$(gh repo view --json owner --jq .owner.login)
REPO="$OWNER/$REPO_NAME"
echo "Repositorio: https://github.com/$REPO"

echo "==> 2/5 Creando labels"
gh label create "RF" --color 1D76DB --description "Implementa requerimiento funcional" 2>/dev/null || true
gh label create "RNF" --color 5319E7 --description "Requerimiento no funcional" 2>/dev/null || true
gh label create "docs" --color 0E8A16 --description "Documentacion" 2>/dev/null || true
gh label create "bug" --color D73A4A --description "Defecto" 2>/dev/null || true
gh label create "camino-critico" --color B60205 --description "Bloquea la fecha de entrega" 2>/dev/null || true
gh label create "agente:CORE" --color 0E8A16 --description "Workstream del agente CORE" 2>/dev/null || true
gh label create "agente:FRONT" --color 1D76DB --description "Workstream del agente FRONT" 2>/dev/null || true
gh label create "agente:CAM" --color 5319E7 --description "Workstream del agente CAM" 2>/dev/null || true
gh label create "agente:EDGE" --color B60205 --description "Workstream del agente EDGE" 2>/dev/null || true
gh label create "agente:ML" --color D93F0B --description "Workstream del agente ML" 2>/dev/null || true
gh label create "agente:RULES" --color 0052CC --description "Workstream del agente RULES" 2>/dev/null || true
gh label create "agente:BINS" --color 006B75 --description "Workstream del agente BINS" 2>/dev/null || true
gh label create "agente:DATA" --color 5D4037 --description "Workstream del agente DATA" 2>/dev/null || true
gh label create "agente:QA" --color FBCA04 --description "Workstream del agente QA" 2>/dev/null || true
gh label create "agente:RELEASE" --color C2185B --description "Workstream del agente RELEASE" 2>/dev/null || true

echo "==> 3/5 Creando milestones"
MS0=$(gh api repos/$REPO/milestones -f title="M0: Fundación y contratos" -f due_on="2026-08-14T23:59:59Z" -f description="Agente CORE — estructura KMP y contratos entre agentes. Único tramo secuencial: desbloquea a todos los demás." --jq .title 2>/dev/null || echo "M0: Fundación y contratos")
MS1=$(gh api repos/$REPO/milestones -f title="M1: App shell y design system" -f due_on="2026-09-18T23:59:59Z" -f description="Agente FRONT — CUS-001, CUS-003, CUS-007, CUS-008" --jq .title 2>/dev/null || echo "M1: App shell y design system")
MS2=$(gh api repos/$REPO/milestones -f title="M2: Cámara y calidad de imagen" -f due_on="2026-09-11T23:59:59Z" -f description="Agente CAM — CUS-004, CUS-005" --jq .title 2>/dev/null || echo "M2: Cámara y calidad de imagen")
MS3=$(gh api repos/$REPO/milestones -f title="M3: Inferencia on-device y gamas" -f due_on="2026-10-02T23:59:59Z" -f description="Agente EDGE — CUS-003, CUS-005, CUS-008" --jq .title 2>/dev/null || echo "M3: Inferencia on-device y gamas")
MS4=$(gh api repos/$REPO/milestones -f title="M4: Modelos y datos" -f due_on="2026-10-09T23:59:59Z" -f description="Agente ML — CUS-003, CUS-005. Camino crítico del proyecto." --jq .title 2>/dev/null || echo "M4: Modelos y datos")
MS5=$(gh api repos/$REPO/milestones -f title="M5: Motor de reglas y perfiles" -f due_on="2026-09-04T23:59:59Z" -f description="Agente RULES — CUS-001, CUS-002, CUS-003, CUS-005" --jq .title 2>/dev/null || echo "M5: Motor de reglas y perfiles")
MS6=$(gh api repos/$REPO/milestones -f title="M6: Escaneo de canecas" -f due_on="2026-09-25T23:59:59Z" -f description="Agente BINS — CUS-002" --jq .title 2>/dev/null || echo "M6: Escaneo de canecas")
MS7=$(gh api repos/$REPO/milestones -f title="M7: Persistencia, historial y auth" -f due_on="2026-09-04T23:59:59Z" -f description="Agente DATA — CUS-009, CUS-010" --jq .title 2>/dev/null || echo "M7: Persistencia, historial y auth")
MS8=$(gh api repos/$REPO/milestones -f title="M8: Confianza, integración y QA" -f due_on="2026-10-23T23:59:59Z" -f description="Agente QA — CUS-006 y verificación de todos los RNF medibles" --jq .title 2>/dev/null || echo "M8: Confianza, integración y QA")
MS9=$(gh api repos/$REPO/milestones -f title="M9: Preparación iOS y demo" -f due_on="2026-10-30T23:59:59Z" -f description="Agente RELEASE — cierre de la versión 1,0" --jq .title 2>/dev/null || echo "M9: Preparación iOS y demo")

echo "==> 4/5 Creando issues (una por sesion de trabajo del plan)"

gh issue create \
  --title "S01 · Estructura Kotlin Multiplatform, version catalog e integración continua" \
  --milestone "M0: Fundación y contratos" \
  --label "agente:CORE" \
  --label "RNF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El proyecto compila y ejecuta pruebas en integración continua, con los módulos shared y androidApp separados y sin dependencias cruzadas indebidas.

## Requerimientos que implementa
- Requerimientos: RNF-005, RNF-015
- Casos de uso relacionados: —

## Tareas
- [ ] Crear los módulos `shared` y `androidApp` con el plugin de Kotlin Multiplatform
- [ ] Configurar el version catalog en `gradle/libs.versions.toml`
- [ ] Añadir Koin, kotlinx.serialization y SQLDelight al catálogo
- [ ] Configurar el flujo de integración continua que compila y ejecuta pruebas
- [ ] Añadir una regla de verificación que falle si aparece un import de plataforma en `shared`

## Criterio de hecho
`./gradlew :androidApp:assembleDebug` y `./gradlew :shared:allTests` pasan en integración continua, y la verificación de imports rechaza cualquier `android.*` dentro de `shared`.

## Estimación
8 horas — agente CORE, hito «M0: Fundación y contratos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CORE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S01"

gh issue create \
  --title "S02 · Contratos de dominio, puertos y fakes deterministas" \
  --milestone "M0: Fundación y contratos" \
  --label "agente:CORE" \
  --label "RNF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Las seis interfaces que conectan a los agentes quedan publicadas con implementaciones simuladas deterministas, de modo que ningún agente tenga que esperar a otro.

## Requerimientos que implementa
- Requerimientos: RNF-015
- Casos de uso relacionados: —

## Tareas
- [ ] Declarar `WasteClassifier`, `BinDetector`, `FrameQualityAnalyzer`, `RuleEngine`, `DeviceTierPolicy` y `AuthProvider`
- [ ] Declarar las entidades de dominio: `WasteMaterial`, `DisposalRoute`, `Disposal`, `ClassificationResult`, `ContaminationResult`, `FrameQuality`, `DetectedBin`
- [ ] Implementar un fake determinista de cada puerto en `shared/testing/`
- [ ] Escribir una prueba por fake que documente su comportamiento esperado

## Criterio de hecho
Cada puerto tiene su fake y su prueba; un agente puede compilar y probar su módulo usando únicamente fakes de los vecinos.

## Estimación
8 horas — agente CORE, hito «M0: Fundación y contratos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CORE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S02"

gh issue create \
  --title "S03 · Esquema del perfil normativo, taxonomía de materiales y perfil de Colombia" \
  --milestone "M0: Fundación y contratos" \
  --label "agente:CORE" \
  --label "RF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Queda definido el formato de los perfiles normativos y publicado el perfil de Colombia conforme a la Resolución 2184 de 2019.

## Requerimientos que implementa
- Requerimientos: RF-002, RNF-004
- Casos de uso relacionados: CUS-001

## Tareas
- [ ] Definir el esquema JSON del perfil: canecas, reglas por material, reglas de inspección y ruta conservadora
- [ ] Definir el enumerado `WasteMaterial` como vocabulario compartido entre modelo y motor de reglas
- [ ] Escribir `co.json` con las canecas blanca, negra y verde y sus reglas
- [ ] Declarar la regla de inspección del cartón para bebidas con su caneca alternativa por contaminación
- [ ] Añadir la validación del perfil contra el esquema

## Criterio de hecho
`co.json` valida contra el esquema, cubre las tres canecas de la Resolución 2184 y declara explícitamente el caso del vaso de cartón limpio frente a contaminado.

## Estimación
8 horas — agente CORE, hito «M0: Fundación y contratos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CORE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S03"

gh issue create \
  --title "S04 · Design system de estética iOS" \
  --milestone "M1: App shell y design system" \
  --label "agente:FRONT" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Existe un sistema de diseño minimalista de inspiración iOS del que sale todo componente visual de la aplicación.

## Requerimientos que implementa
- Requerimientos: RNF-009
- Casos de uso relacionados: CUS-003, CUS-007

## Tareas
- [ ] Definir la escala tipográfica, la paleta, el espaciado y los radios
- [ ] Implementar los componentes base: botón, tarjeta, hoja inferior, indicador y píldora de estado
- [ ] Definir el tema claro y oscuro
- [ ] Documentar el uso del sistema en `docs/design-system.md`

## Criterio de hecho
Ningún color ni tipografía se declara fuera del design system, verificado por inspección del código de la capa de interfaz.

## Estimación
10 horas — agente FRONT, hito «M1: App shell y design system», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente FRONT y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S04"

gh issue create \
  --title "S05 · Navegación y onboarding de selección de país" \
  --milestone "M1: App shell y design system" \
  --label "agente:FRONT" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El usuario selecciona su país en el primer arranque y puede cambiarlo después desde ajustes, recargando el perfil activo.

## Requerimientos que implementa
- Requerimientos: RF-001, RF-003
- Casos de uso relacionados: CUS-001

## Tareas
- [ ] Implementar el grafo de navegación de la aplicación
- [ ] Construir la pantalla de selección de país a partir del catálogo de perfiles
- [ ] Conectar la selección al caso de uso de carga de perfil
- [ ] Implementar el cambio de país desde ajustes con reinicio de canecas disponibles

## Criterio de hecho
El primer arranque solicita país; cambiarlo desde ajustes recarga el perfil y limpia el conjunto de canecas registrado.

## Estimación
8 horas — agente FRONT, hito «M1: App shell y design system», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente FRONT y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S05"

gh issue create \
  --title "S06 · Pantalla de cámara con superposiciones e indicaciones" \
  --milestone "M1: App shell y design system" \
  --label "agente:FRONT" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La pantalla principal muestra la vista en vivo con el resultado y las indicaciones de captura sin obstruir la imagen.

## Requerimientos que implementa
- Requerimientos: RF-009, RF-013, RF-017
- Casos de uso relacionados: CUS-003, CUS-004

## Tareas
- [ ] Construir la pantalla de cámara con la vista previa a pantalla completa
- [ ] Implementar la superposición de resultado con caneca, color y categoría
- [ ] Implementar la presentación de indicaciones de captura de forma no intrusiva
- [ ] Conectar la pantalla al caso de uso de clasificación mediante su ViewModel

## Criterio de hecho
La vista en vivo no se bloquea durante el análisis y las indicaciones aparecen y desaparecen sin desplazar el contenido.

## Estimación
10 horas — agente FRONT, hito «M1: App shell y design system», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente FRONT y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S06"

gh issue create \
  --title "S07 · Pantalla de resultado con justificación normativa" \
  --milestone "M1: App shell y design system" \
  --label "agente:FRONT" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El usuario puede ver por qué se asignó esa caneca, con la regla aplicada, la referencia normativa y el aviso de carácter orientativo.

## Requerimientos que implementa
- Requerimientos: RF-026, RF-027, RF-028
- Casos de uso relacionados: CUS-007

## Tareas
- [ ] Construir el detalle de la decisión con material, regla y referencia normativa
- [ ] Indicar cuándo la decisión se degradó por contaminación o por ausencia de la caneca ideal
- [ ] Mostrar el aviso de carácter orientativo de forma visible
- [ ] Marcar explícitamente los resultados provenientes de selección manual

## Criterio de hecho
El detalle muestra caneca, color, regla aplicada, norma citada y aviso; un resultado manual se distingue de uno automático.

## Estimación
6 horas — agente FRONT, hito «M1: App shell y design system», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente FRONT y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S07"

gh issue create \
  --title "S08 · Ajustes: país, nivel de rendimiento y gestión del historial" \
  --milestone "M1: App shell y design system" \
  --label "agente:FRONT" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El usuario controla desde una sola pantalla el país activo, el nivel de rendimiento y el borrado del historial.

## Requerimientos que implementa
- Requerimientos: RF-003, RF-031, RF-034
- Casos de uso relacionados: CUS-001, CUS-008, CUS-009

## Tareas
- [ ] Construir la pantalla de ajustes
- [ ] Conectar el selector de país al repositorio de preferencias
- [ ] Conectar el ajuste de rendimiento a la política de gama, con advertencia del efecto
- [ ] Implementar el borrado del historial con confirmación explícita

## Criterio de hecho
Los tres ajustes persisten entre reinicios de la aplicación y el borrado del historial requiere confirmación.

## Estimación
6 horas — agente FRONT, hito «M1: App shell y design system», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente FRONT y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S08"

gh issue create \
  --title "S09 · Accesibilidad e internacionalización" \
  --milestone "M1: App shell y design system" \
  --label "agente:FRONT" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La interfaz es accesible y está completamente externalizada para admitir idiomas adicionales sin tocar código.

## Requerimientos que implementa
- Requerimientos: RNF-010, RNF-011
- Casos de uso relacionados: CUS-007

## Tareas
- [ ] Extraer todo literal visible a recursos de cadenas
- [ ] Verificar contraste AA en tema claro y oscuro
- [ ] Añadir descripciones de contenido para lector de pantalla
- [ ] Comprobar el comportamiento con tamaños de fuente ampliados
- [ ] Añadir texto e icono a la comunicación de la caneca, además del color

## Criterio de hecho
Cero literales de texto en el código de interfaz, contraste AA verificado y ninguna información esencial transmitida solo por color.

## Estimación
8 horas — agente FRONT, hito «M1: App shell y design system», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente FRONT y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S09"

gh issue create \
  --title "S10 · Integración de CameraX y flujo de fotogramas" \
  --milestone "M2: Cámara y calidad de imagen" \
  --label "agente:CAM" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Los fotogramas de la cámara llegan al dominio de forma continua, con gestión correcta del ciclo de vida y sin fugas de memoria.

## Requerimientos que implementa
- Requerimientos: RF-009
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Configurar CameraX con caso de uso de previsualización y de análisis de imagen
- [ ] Implementar la conversión de fotograma al tipo `ImageFrame` del dominio
- [ ] Gestionar permisos de cámara y el estado de denegación
- [ ] Liberar recursos correctamente al salir de la pantalla

## Criterio de hecho
Una sesión continua de 5 minutos no incrementa la memoria de forma sostenida y la denegación de permiso se maneja sin cierre inesperado.

## Estimación
8 horas — agente CAM, hito «M2: Cámara y calidad de imagen», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CAM y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S10"

gh issue create \
  --title "S11 · Métricas de nitidez, luminancia y encuadre" \
  --milestone "M2: Cámara y calidad de imagen" \
  --label "agente:CAM" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El sistema evalúa la calidad de cada fotograma con heurísticas baratas, sin consumir presupuesto de latencia de la clasificación.

## Requerimientos que implementa
- Requerimientos: RF-015
- Casos de uso relacionados: CUS-004

## Tareas
- [ ] Implementar la varianza del Laplaciano como medida de nitidez
- [ ] Implementar la luminancia media y la detección de sobreexposición y subexposición
- [ ] Implementar la comprobación de que el objeto está dentro del área útil
- [ ] Calibrar los umbrales con un conjunto de fotogramas de prueba

## Criterio de hecho
`FrameQuality` identifica correctamente desenfoque, baja luz y mal encuadre sobre el conjunto de fotogramas de prueba.

## Estimación
8 horas — agente CAM, hito «M2: Cámara y calidad de imagen», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CAM y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S11"

gh issue create \
  --title "S12 · Detección de suciedad persistente en el lente" \
  --milestone "M2: Cámara y calidad de imagen" \
  --label "agente:CAM" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El sistema distingue una mancha fija en el lente de un objeto estático presente en la escena.

## Requerimientos que implementa
- Requerimientos: RF-016
- Casos de uso relacionados: CUS-004

## Tareas
- [ ] Implementar la comparación de regiones de baja varianza entre fotogramas consecutivos
- [ ] Descartar como suciedad las regiones que se desplazan al mover la cámara
- [ ] Definir el umbral de persistencia que dispara la sugerencia de limpieza

## Criterio de hecho
Una mancha simulada fija se detecta como suciedad y un objeto estático de la escena no genera falso positivo.

## Estimación
6 horas — agente CAM, hito «M2: Cámara y calidad de imagen», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CAM y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S12"

gh issue create \
  --title "S13 · Motor de indicaciones con política anti-saturación" \
  --milestone "M2: Cámara y calidad de imagen" \
  --label "agente:CAM" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El usuario recibe una sola indicación relevante a la vez, con un intervalo mínimo entre ellas, y desaparece al corregirse la condición.

## Requerimientos que implementa
- Requerimientos: RF-017, RF-018
- Casos de uso relacionados: CUS-004

## Tareas
- [ ] Implementar la selección de la causa dominante de degradación
- [ ] Implementar el intervalo mínimo entre indicaciones consecutivas
- [ ] Retirar la indicación en cuanto la métrica vuelve al rango aceptable
- [ ] Suprimir las indicaciones cuando la confianza de clasificación ya es suficiente

## Criterio de hecho
Como máximo una indicación cada intervalo mínimo, nunca dos simultáneas, y ninguna cuando todas las métricas son aceptables.

## Estimación
8 horas — agente CAM, hito «M2: Cámara y calidad de imagen», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CAM y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S13"

gh issue create \
  --title "S14 · Captura dirigida para inspección interior" \
  --milestone "M2: Cámara y calidad de imagen" \
  --label "agente:CAM" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Cuando la regla de inspección lo requiere, el sistema solicita y captura la vista del interior o de la superficie crítica del residuo.

## Requerimientos que implementa
- Requerimientos: RF-020
- Casos de uso relacionados: CUS-004, CUS-005

## Tareas
- [ ] Implementar el modo de captura dirigida con su indicación en pantalla
- [ ] Tomar el texto de la solicitud desde el perfil, pasando por recursos de cadenas
- [ ] Entregar el fotograma dirigido al puerto de clasificación de contaminación
- [ ] Gestionar el caso en que el usuario no proporciona la toma

## Criterio de hecho
Ante un material con regla de inspección se solicita la vista interior, se captura y se entrega; si el usuario no la da, se aplica la ruta conservadora.

## Estimación
6 horas — agente CAM, hito «M2: Cámara y calidad de imagen», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente CAM y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S14"

gh issue create \
  --title "S15 · Integración de LiteRT con delegados y respaldo en procesador" \
  --milestone "M3: Inferencia on-device y gamas" \
  --label "agente:EDGE" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El modelo de clasificación de material se ejecuta en el dispositivo, aprovechando aceleración cuando existe y cayendo a procesador cuando no.

## Requerimientos que implementa
- Requerimientos: RF-011, RF-014
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Integrar el motor LiteRT y la carga de modelos empaquetados
- [ ] Configurar los delegados NNAPI y GPU con detección de disponibilidad
- [ ] Implementar el respaldo automático en procesador
- [ ] Implementar el preprocesamiento de la imagen conforme al modelo
- [ ] Verificar el funcionamiento en modo avión

## Criterio de hecho
Clasifica correctamente sin conexión y, si el delegado no está disponible, cae a procesador sin fallar ni informar error al usuario.

## Estimación
12 horas — agente EDGE, hito «M3: Inferencia on-device y gamas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente EDGE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S15"

gh issue create \
  --title "S16 · Detección y recorte del objeto en el encuadre" \
  --milestone "M3: Inferencia on-device y gamas" \
  --label "agente:EDGE" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El objeto principal se aísla antes de clasificarlo, con una alternativa sin detector para gama baja.

## Requerimientos que implementa
- Requerimientos: RF-010
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Integrar el detector ligero de objeto y el recorte del área de interés
- [ ] Implementar el marco guía fijo como alternativa para gama baja
- [ ] Conectar la elección de estrategia a la política de gama
- [ ] Medir el coste en latencia del detector

## Criterio de hecho
En gama media y alta se recorta el objeto detectado; en gama baja se usa el marco guía y la clasificación sigue funcionando.

## Estimación
10 horas — agente EDGE, hito «M3: Inferencia on-device y gamas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente EDGE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S16"

gh issue create \
  --title "S17 · Política de gama con micro-benchmark de arranque" \
  --milestone "M3: Inferencia on-device y gamas" \
  --label "agente:EDGE" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La aplicación determina la gama real del dispositivo midiendo su latencia efectiva, no solo leyendo sus especificaciones.

## Requerimientos que implementa
- Requerimientos: RF-029
- Casos de uso relacionados: CUS-008

## Tareas
- [ ] Leer memoria total, número de núcleos, nivel de API y delegados disponibles
- [ ] Ejecutar N inferencias de calentamiento y medir la latencia media
- [ ] Combinar las señales en una decisión de gama baja, media o alta
- [ ] Cachear el resultado y permitir su recálculo si la latencia se degrada

## Criterio de hecho
La gama se resuelve en menos de 2 segundos al arrancar, queda cacheada y se recalcula a la baja si la latencia observada se degrada de forma sostenida.

## Estimación
8 horas — agente EDGE, hito «M3: Inferencia on-device y gamas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente EDGE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S17"

gh issue create \
  --title "S18 · Activación escalonada de funciones por gama" \
  --milestone "M3: Inferencia on-device y gamas" \
  --label "agente:EDGE" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Cada función costosa consulta la política de gama antes de activarse, y la clasificación por cámara permanece disponible siempre.

## Requerimientos que implementa
- Requerimientos: RF-030, RF-031
- Casos de uso relacionados: CUS-008

## Tareas
- [ ] Implementar la matriz de funciones habilitadas por gama
- [ ] Conectar cámara, detector y etapa de contaminación a la consulta de gama
- [ ] Implementar la sobrescritura manual del nivel desde ajustes
- [ ] Añadir pruebas que verifiquen que la clasificación por cámara está activa en las tres gamas

## Criterio de hecho
La matriz de funciones se respeta en las tres gamas y ninguna combinación deshabilita la clasificación por cámara.

## Estimación
6 horas — agente EDGE, hito «M3: Inferencia on-device y gamas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente EDGE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S18"

gh issue create \
  --title "S19 · Etapa de contaminación en el dispositivo" \
  --milestone "M3: Inferencia on-device y gamas" \
  --label "agente:EDGE" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El segundo modelo evalúa si el residuo aprovechable está limpio o contaminado, sobre el recorte o la toma dirigida.

## Requerimientos que implementa
- Requerimientos: RF-021
- Casos de uso relacionados: CUS-005

## Tareas
- [ ] Integrar el modelo binario de contaminación
- [ ] Ejecutarlo sobre el recorte del objeto o sobre la toma dirigida
- [ ] Devolver el estado con su nivel de confianza
- [ ] Restringir la ejecución automática según la gama del dispositivo

## Criterio de hecho
Devuelve estado de contaminación con confianza; en gama baja se ejecuta únicamente en captura manual dirigida.

## Estimación
10 horas — agente EDGE, hito «M3: Inferencia on-device y gamas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente EDGE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S19"

gh issue create \
  --title "S20 · Optimización de latencia, memoria y consumo" \
  --milestone "M3: Inferencia on-device y gamas" \
  --label "agente:EDGE" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El recorrido completo de clasificación cumple el presupuesto de latencia y memoria previsto para cada gama.

## Requerimientos que implementa
- Requerimientos: RNF-001, RNF-007
- Casos de uso relacionados: CUS-003, CUS-008

## Tareas
- [ ] Instrumentar la medición de latencia extremo a extremo
- [ ] Reutilizar buffers y evitar asignaciones en el bucle de análisis
- [ ] Ajustar la frecuencia de análisis por gama
- [ ] Registrar latencia y memoria máxima para las tres gamas

## Criterio de hecho
Latencia y memoria medidas y documentadas por gama; el uso máximo de memoria no supera los 350 MB en clasificación continua.

## Estimación
12 horas — agente EDGE, hito «M3: Inferencia on-device y gamas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente EDGE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S20"

gh issue create \
  --title "S21 · Inventario de datasets públicos, licencias y mapeo de taxonomía" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RNF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Queda documentado qué conjuntos de datos públicos se usan, bajo qué licencia, y cómo se traducen sus etiquetas a la taxonomía del proyecto.

## Requerimientos que implementa
- Requerimientos: RNF-016, RNF-017
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Inventariar y descargar los conjuntos candidatos: Garbage Dataset v2, Garbage Classification, RealWaste, TrashNet, TACO y ZeroWaste
- [ ] Verificar y documentar la licencia de cada uno en `ml/DATASETS.md`
- [ ] Escribir `ml/taxonomy/label_mapping.yaml` con la traducción a `WasteMaterial`
- [ ] Descartar explícitamente los conjuntos cuya licencia no sea compatible

## Criterio de hecho
`ml/DATASETS.md` y `label_mapping.yaml` están completos, revisados, y toda etiqueta de origen tiene destino o descarte justificado.

## Estimación
8 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S21"

gh issue create \
  --title "S22 · Pipeline de ingesta, unificación y particiones reproducibles" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RNF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El conjunto de entrenamiento se construye de forma reproducible desde cero, con particiones versionadas y validación cruzada por dataset.

## Requerimientos que implementa
- Requerimientos: RNF-016
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Implementar la ingesta y normalización de cada conjunto de origen
- [ ] Aplicar el mapeo de taxonomía y detectar conflictos de etiqueta
- [ ] Generar particiones con semilla fija, incluida una partición de control de un dataset no visto
- [ ] Registrar el balance de clases resultante

## Criterio de hecho
Ejecutar el pipeline dos veces desde cero produce particiones idénticas, y existe una partición de control procedente de un dataset excluido del entrenamiento.

## Estimación
10 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S22"

gh issue create \
  --title "S23 · Augmentación orientada al dominio móvil real" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RNF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El conjunto de entrenamiento refleja las condiciones adversas reales de una cámara de teléfono sostenida a mano.

## Requerimientos que implementa
- Requerimientos: RNF-008
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Implementar desenfoque gaussiano y de movimiento
- [ ] Implementar variación de brillo, contraste y temperatura de color
- [ ] Implementar ruido, artefactos de compresión, oclusión parcial y perspectiva
- [ ] Revisar visualmente una muestra del resultado y ajustar intensidades

## Criterio de hecho
La muestra revisada es visualmente comparable a fotos reales de teléfono en malas condiciones, sin degradar el objeto hasta hacerlo irreconocible.

## Estimación
8 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S23"

gh issue create \
  --title "S24 · Síntesis de contaminación por segmentación y composición" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Se genera un conjunto de reciclables contaminados a partir de imágenes de reciclables limpios, ante la inexistencia de conjuntos públicos de este tipo.

## Requerimientos que implementa
- Requerimientos: RF-021
- Casos de uso relacionados: CUS-005

## Tareas
- [ ] Integrar la segmentación del objeto limpio con U²-Net
- [ ] Construir una biblioteca de texturas de líquido, grasa y residuo alimenticio
- [ ] Componer las texturas sobre la superficie del objeto de forma realista
- [ ] Generar el conjunto sintético y reservar un conjunto de control de contaminación real recopilada de fuentes públicas
- [ ] Revisar por muestreo la verosimilitud del resultado

## Criterio de hecho
Existe un conjunto sintético de reciclables contaminados y un conjunto de control independiente para evaluar si la síntesis transfiere a suciedad real.

## Estimación
16 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S24"

gh issue create \
  --title "S25 · Entrenamiento del clasificador de material por variante de gama" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Existen tres variantes del clasificador de material, una por gama de dispositivo, con métricas registradas.

## Requerimientos que implementa
- Requerimientos: RF-011, RNF-008
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Entrenar por transferencia desde pesos preentrenados, con ajuste en dos fases
- [ ] Entrenar MobileNetV3-Small, MobileNetV3-Large 0.75 y EfficientNet-Lite2
- [ ] Registrar exactitud top-1 de material y acierto de ruta de disposición
- [ ] Analizar la matriz de confusión y las clases con peor desempeño

## Criterio de hecho
Tres variantes entrenadas con métricas registradas, incluyendo siempre el acierto de ruta además del top-1 de material.

## Estimación
16 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S25"

gh issue create \
  --title "S26 · Entrenamiento del clasificador de contaminación" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Existe un clasificador binario que distingue reciclables limpios de contaminados, evaluado sobre contaminación real y no solo sintética.

## Requerimientos que implementa
- Requerimientos: RF-021
- Casos de uso relacionados: CUS-005

## Tareas
- [ ] Entrenar el clasificador binario sobre el conjunto sintético
- [ ] Evaluar sobre el conjunto de control de contaminación real
- [ ] Ajustar el umbral de decisión priorizando no clasificar como limpio algo contaminado
- [ ] Documentar la brecha entre desempeño sintético y real

## Criterio de hecho
El clasificador separa limpio de contaminado y la brecha entre el conjunto sintético y el de control real está medida y documentada.

## Estimación
10 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S26"

gh issue create \
  --title "S27 · Cuantización INT8 y exportación a LiteRT" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RNF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Los modelos quedan exportados en formato ejecutable en el dispositivo, cuantizados y dentro del presupuesto de tamaño.

## Requerimientos que implementa
- Requerimientos: RNF-001, RNF-006
- Casos de uso relacionados: CUS-003, CUS-008

## Tareas
- [ ] Aplicar cuantización posterior al entrenamiento con conjunto representativo
- [ ] Exportar las tres variantes de material y la de contaminación a LiteRT
- [ ] Medir la pérdida de exactitud introducida por la cuantización
- [ ] Verificar que el tamaño total empaquetado cabe en el presupuesto de la aplicación

## Criterio de hecho
Los cuatro modelos exportados funcionan en el dispositivo, la pérdida por cuantización está documentada y el paquete no supera los 150 MB.

## Estimación
10 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S27"

gh issue create \
  --title "S28 · Evaluación cruzada por dataset y reporte de métricas" \
  --milestone "M4: Modelos y datos" \
  --label "agente:ML" \
  --label "RNF" \
  --label "camino-critico" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Existe evidencia de generalización real, medida sobre datos no vistos durante el entrenamiento.

## Requerimientos que implementa
- Requerimientos: RNF-008, RNF-016
- Casos de uso relacionados: CUS-003, CUS-005

## Tareas
- [ ] Evaluar sobre la partición de control de un dataset excluido del entrenamiento
- [ ] Reportar exactitud top-1 de material y acierto de ruta de disposición
- [ ] Reportar el desempeño del recorrido completo sobre reciclables contaminados
- [ ] Publicar el reporte en `ml/REPORTE_METRICAS.md`

## Criterio de hecho
El reporte publica ambas métricas sobre un conjunto no visto y explicita si se alcanza el 85 % en material y el 95 % en ruta exigidos por RNF-008.

## Estimación
10 horas — agente ML, hito «M4: Modelos y datos», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente ML y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S28"

gh issue create \
  --title "S29 · Motor de reglas: resolución de material a caneca destino" \
  --milestone "M5: Motor de reglas y perfiles" \
  --label "agente:RULES" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El motor traduce material, estado de contaminación, canecas disponibles y perfil activo en una caneca concreta con su justificación.

## Requerimientos que implementa
- Requerimientos: RF-012
- Casos de uso relacionados: CUS-003

## Tareas
- [ ] Implementar `RuleEngine` con la resolución material a ruta a caneca
- [ ] Implementar la generación de la justificación de cada decisión
- [ ] Escribir la batería de pruebas sobre el perfil de Colombia
- [ ] Incluir la prueba explícita del vaso de cartón limpio frente a contaminado

## Criterio de hecho
La batería de pruebas pasa sobre el perfil de Colombia, incluido el caso del cartón para bebidas que cambia de caneca según su estado.

## Estimación
10 horas — agente RULES, hito «M5: Motor de reglas y perfiles», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente RULES y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S29"

gh issue create \
  --title "S30 · Carga, validación y extensión del catálogo de perfiles" \
  --milestone "M5: Motor de reglas y perfiles" \
  --label "agente:RULES" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Los perfiles se cargan y validan desde recursos locales, y añadir un país no requiere tocar código.

## Requerimientos que implementa
- Requerimientos: RF-002, RF-004
- Casos de uso relacionados: CUS-001

## Tareas
- [ ] Implementar la carga de perfiles desde recursos con kotlinx.serialization
- [ ] Implementar la validación contra el esquema con errores descriptivos
- [ ] Implementar el catálogo de países disponibles
- [ ] Añadir una prueba que verifique que un perfil inválido no tumba la aplicación

## Criterio de hecho
Un perfil inválido se rechaza con un error explícito, se conserva el perfil anterior y la aplicación sigue operativa.

## Estimación
8 horas — agente RULES, hito «M5: Motor de reglas y perfiles», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente RULES y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S30"

gh issue create \
  --title "S31 · Reglas de inspección y reclasificación por contaminación" \
  --milestone "M5: Motor de reglas y perfiles" \
  --label "agente:RULES" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El perfil declara qué materiales requieren inspección y a qué caneca van si resultan contaminados; el motor lo aplica.

## Requerimientos que implementa
- Requerimientos: RF-019, RF-022
- Casos de uso relacionados: CUS-005

## Tareas
- [ ] Implementar la evaluación de reglas de inspección del perfil
- [ ] Implementar la reasignación a la caneca alternativa por contaminación
- [ ] Marcar la decisión como degradada por contaminación
- [ ] Probar el caso en que no se pudo verificar el interior

## Criterio de hecho
Un reciclable detectado como contaminado se reasigna a la caneca de no aprovechables con su justificación, y el caso no verificado aplica la ruta conservadora.

## Estimación
8 horas — agente RULES, hito «M5: Motor de reglas y perfiles», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente RULES y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S31"

gh issue create \
  --title "S32 · Restricción a canecas disponibles con respaldo conservador" \
  --milestone "M5: Motor de reglas y perfiles" \
  --label "agente:RULES" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La recomendación se limita a las canecas que realmente existen en el entorno, con una alternativa razonada cuando falta la ideal.

## Requerimientos que implementa
- Requerimientos: RF-008
- Casos de uso relacionados: CUS-002, CUS-003

## Tareas
- [ ] Implementar el filtrado de la decisión por el conjunto de canecas disponibles
- [ ] Implementar la elección de la alternativa más conservadora
- [ ] Generar el mensaje que explica por qué no se recomendó la caneca ideal
- [ ] Probar los escenarios de una, dos y tres canecas disponibles

## Criterio de hecho
Ante la ausencia de la caneca ideal se propone la disponible más conservadora y se informa el motivo al usuario.

## Estimación
8 horas — agente RULES, hito «M5: Motor de reglas y perfiles», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente RULES y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S32"

gh issue create \
  --title "S33 · Perfil de un segundo país como prueba de escalabilidad" \
  --milestone "M5: Motor de reglas y perfiles" \
  --label "agente:RULES" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Se demuestra que incorporar un país nuevo es exclusivamente un trabajo de datos.

## Requerimientos que implementa
- Requerimientos: RNF-004
- Casos de uso relacionados: CUS-001

## Tareas
- [ ] Investigar y documentar el código de colores del segundo país
- [ ] Escribir su archivo de perfil y registrarlo en el catálogo
- [ ] Ejecutar la batería de pruebas del motor contra el nuevo perfil
- [ ] Verificar por inspección que no hubo cambios en código Kotlin

## Criterio de hecho
El segundo país funciona completo sin una sola línea modificada en código Kotlin, verificado en el diff del cambio.

## Estimación
6 horas — agente RULES, hito «M5: Motor de reglas y perfiles», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente RULES y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S33"

gh issue create \
  --title "S34 · Detector de canecas por color y forma" \
  --milestone "M6: Escaneo de canecas" \
  --label "agente:BINS" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La aplicación reconoce por cámara qué canecas hay en el entorno y las empareja con las declaradas en el perfil activo.

## Requerimientos que implementa
- Requerimientos: RF-005, RF-006
- Casos de uso relacionados: CUS-002

## Tareas
- [ ] Implementar la detección de regiones de color de caneca robusta a iluminación variable
- [ ] Implementar el emparejamiento con las `BinDefinition` del perfil activo
- [ ] Descartar colores que no pertenezcan al perfil e informarlo
- [ ] Evaluar el detector bajo distintas condiciones de luz

## Criterio de hecho
Detecta las canecas del perfil activo bajo iluminación variable y descarta con mensaje los colores ajenos al estándar del país.

## Estimación
12 horas — agente BINS, hito «M6: Escaneo de canecas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente BINS y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S34"

gh issue create \
  --title "S35 · Confirmación, edición manual y persistencia de las canecas" \
  --milestone "M6: Escaneo de canecas" \
  --label "agente:BINS" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
El reconocimiento propone y el usuario decide: la selección final de canecas queda confirmada y persistida.

## Requerimientos que implementa
- Requerimientos: RF-007
- Casos de uso relacionados: CUS-002

## Tareas
- [ ] Construir la pantalla de confirmación del conjunto reconocido
- [ ] Permitir añadir y eliminar canecas manualmente desde el perfil
- [ ] Persistir la selección en el repositorio de disponibilidad
- [ ] Gestionar el caso en que no se reconoce ninguna caneca

## Criterio de hecho
El usuario confirma, añade o elimina canecas, la selección persiste entre reinicios y omitir el escaneo asume todas las del perfil.

## Estimación
8 horas — agente BINS, hito «M6: Escaneo de canecas», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente BINS y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S35"

gh issue create \
  --title "S36 · Persistencia local con SQLDelight, DataStore y repositorios" \
  --milestone "M7: Persistencia, historial y auth" \
  --label "agente:DATA" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La configuración, las canecas disponibles y el historial sobreviven al cierre de la aplicación.

## Requerimientos que implementa
- Requerimientos: RNF-014
- Casos de uso relacionados: CUS-001, CUS-002, CUS-009

## Tareas
- [ ] Definir el esquema de SQLDelight para el historial
- [ ] Implementar el repositorio de preferencias sobre DataStore
- [ ] Implementar el repositorio de disponibilidad de canecas
- [ ] Añadir pruebas de persistencia entre sesiones

## Criterio de hecho
País, canecas disponibles e historial se recuperan correctamente tras cerrar y reabrir la aplicación.

## Estimación
8 horas — agente DATA, hito «M7: Persistencia, historial y auth», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente DATA y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S36"

gh issue create \
  --title "S37 · Historial local: registro, consulta y borrado" \
  --milestone "M7: Persistencia, historial y auth" \
  --label "agente:DATA" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Cada clasificación queda registrada localmente con su resultado, nunca con la imagen, y el usuario puede consultarla y borrarla.

## Requerimientos que implementa
- Requerimientos: RF-032, RF-033, RF-034
- Casos de uso relacionados: CUS-003, CUS-009

## Tareas
- [ ] Implementar el registro del resultado tras cada clasificación
- [ ] Construir la consulta del historial con material, caneca y fecha
- [ ] Implementar el borrado completo con confirmación
- [ ] Añadir una prueba que verifique que ningún fotograma se persiste

## Criterio de hecho
El historial guarda únicamente resultados, la prueba confirma que no se escribe ninguna imagen a disco y el borrado es efectivo.

## Estimación
8 horas — agente DATA, hito «M7: Persistencia, historial y auth», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente DATA y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S37"

gh issue create \
  --title "S38 · Puerto de autenticación y modo invitado" \
  --milestone "M7: Persistencia, historial y auth" \
  --label "agente:DATA" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La infraestructura de inicio de sesión queda preparada para una versión futura, operando mientras tanto en modo invitado.

## Requerimientos que implementa
- Requerimientos: RF-035, RF-036, RF-037
- Casos de uso relacionados: CUS-010

## Tareas
- [ ] Implementar `AuthProvider` con una implementación de invitado
- [ ] Construir la pantalla de inicio de sesión con la opción de continuar como invitado
- [ ] Informar que la autenticación con credenciales llegará en una versión futura
- [ ] Verificar que ninguna función actual exige cuenta

## Criterio de hecho
La aplicación funciona completa en modo invitado y ninguna capa superior depende de un proveedor de autenticación concreto.

## Estimación
6 horas — agente DATA, hito «M7: Persistencia, historial y auth», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente DATA y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S38"

gh issue create \
  --title "S39 · Umbrales de confianza, respuesta conservadora y selección manual" \
  --milestone "M8: Confianza, integración y QA" \
  --label "agente:QA" \
  --label "RF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Ante la duda el sistema no adivina: pide otra toma, propone la ruta conservadora o deja elegir al usuario.

## Requerimientos que implementa
- Requerimientos: RF-023, RF-024, RF-025
- Casos de uso relacionados: CUS-006

## Tareas
- [ ] Implementar la comparación con el umbral de confianza y la abstención
- [ ] Implementar el reintento de toma y la sugerencia conservadora tras duda persistente
- [ ] Construir la selección manual de categoría
- [ ] Marcar el resultado manual en el detalle y en el historial
- [ ] Calibrar el umbral con las métricas del reporte de modelos

## Criterio de hecho
Por debajo del umbral la aplicación nunca emite una caneca como si fuera certera: pide otra toma, sugiere la conservadora explicando por qué, o deja elegir.

## Estimación
8 horas — agente QA, hito «M8: Confianza, integración y QA», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente QA y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S39"

gh issue create \
  --title "S40 · Integración extremo a extremo y pruebas instrumentadas" \
  --milestone "M8: Confianza, integración y QA" \
  --label "agente:QA" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Los módulos de todos los agentes funcionan juntos sobre dispositivo real, sustituyendo los fakes por implementaciones reales.

## Requerimientos que implementa
- Requerimientos: RNF-013, RNF-015
- Casos de uso relacionados: CUS-001, CUS-002, CUS-003

## Tareas
- [ ] Sustituir los fakes por las implementaciones reales en la configuración de inyección de dependencias
- [ ] Escribir la prueba instrumentada del recorrido completo de selección de país a resultado
- [ ] Probar la degradación controlada ante cámara, delegado o modelo no disponibles
- [ ] Verificar la cobertura de pruebas de la lógica de dominio

## Criterio de hecho
El recorrido completo pasa en dispositivo real, la degradación controlada no cierra la aplicación y la cobertura del dominio alcanza el 70 %.

## Estimación
12 horas — agente QA, hito «M8: Confianza, integración y QA», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente QA y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S40"

gh issue create \
  --title "S41 · Banco de latencia por gama y verificación de requerimientos medibles" \
  --milestone "M8: Confianza, integración y QA" \
  --label "agente:QA" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Existe evidencia medida de cómo se comporta la aplicación en cada gama de dispositivo.

## Requerimientos que implementa
- Requerimientos: RNF-001, RNF-003
- Casos de uso relacionados: CUS-003, CUS-008

## Tareas
- [ ] Construir el banco de medición de latencia extremo a extremo
- [ ] Medir en un dispositivo representativo de cada gama
- [ ] Verificar el funcionamiento desde Android 8.0 y en ARM de 32 y 64 bits
- [ ] Publicar los resultados en `benchmark/RESULTADOS.md`

## Criterio de hecho
Latencia publicada para las tres gamas y confirmación de que la clasificación por cámara funciona en todas, incluso si no se alcanzan los 2 segundos.

## Estimación
10 horas — agente QA, hito «M8: Confianza, integración y QA», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente QA y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S41"

gh issue create \
  --title "S42 · Verificación de privacidad, modo avión y degradación controlada" \
  --milestone "M8: Confianza, integración y QA" \
  --label "agente:QA" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Se confirma que la aplicación no envía nada, no guarda imágenes y no se rompe cuando algo falla.

## Requerimientos que implementa
- Requerimientos: RNF-002, RNF-012, RNF-013
- Casos de uso relacionados: CUS-003, CUS-009

## Tareas
- [ ] Ejecutar el recorrido completo en modo avión
- [ ] Inspeccionar el tráfico de red durante una sesión de clasificación
- [ ] Verificar que no se escriben imágenes a almacenamiento ni a trazas
- [ ] Provocar fallos de cámara, delegado y modelo y comprobar la degradación

## Criterio de hecho
Cero tráfico de red durante la clasificación, ningún fotograma escrito a disco o a logs, y ningún cierre inesperado ante fallos provocados.

## Estimación
6 horas — agente QA, hito «M8: Confianza, integración y QA», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente QA y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S42"

gh issue create \
  --title "S43 · Verificación de compilación del componente compartido para iOS" \
  --milestone "M9: Preparación iOS y demo" \
  --label "agente:RELEASE" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
Se confirma que la fase iOS será implementar adaptadores y no reescribir lógica.

## Requerimientos que implementa
- Requerimientos: RNF-005
- Casos de uso relacionados: CUS-010

## Tareas
- [ ] Añadir el target iOS al módulo `shared`
- [ ] Compilar `shared` para iOS sin modificar código existente
- [ ] Ejecutar las pruebas del motor de reglas y del dominio en ese target
- [ ] Documentar qué puertos quedan pendientes de implementación nativa en iOS

## Criterio de hecho
`shared` compila para iOS sin cambios, sus pruebas de dominio y reglas pasan, y la lista de puertos pendientes está documentada.

## Estimación
8 horas — agente RELEASE, hito «M9: Preparación iOS y demo», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente RELEASE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S43"

gh issue create \
  --title "S44 · Empaquetado de la demo y guion de demostración" \
  --milestone "M9: Preparación iOS y demo" \
  --label "agente:RELEASE" \
  --label "RNF" \
  --body "$(cat <<'BODY'
## Objetivo de la sesión
La versión 1,0 queda lista para mostrarse, con un recorrido de demostración que exhibe las dos funciones esenciales.

## Requerimientos que implementa
- Requerimientos: RNF-006, RNF-009
- Casos de uso relacionados: CUS-002, CUS-003

## Tareas
- [ ] Generar el APK firmado de la versión 1,0
- [ ] Verificar el tamaño del paquete instalado
- [ ] Escribir el guion de demostración que recorre escaneo de canecas y clasificación
- [ ] Incluir en el guion el caso del vaso de cartón limpio frente a contaminado

## Criterio de hecho
APK instalable dentro del presupuesto de tamaño y guion que demuestra escaneo de canecas, clasificación y el caso del vaso contaminado.

## Estimación
8 horas — agente RELEASE, hito «M9: Preparación iOS y demo», según plan/plan_de_trabajo.md

## Antes de empezar
Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente RELEASE y usa los fakes de `shared/testing/` para los módulos de otros agentes.
BODY
  )" || echo "AVISO: fallo la issue S44"

echo "==> 5/5 Listo"
echo "Repositorio:  https://github.com/$REPO"
echo "Milestones:   10"
echo "Issues:       44"
echo "Tablero:      https://github.com/$REPO/issues"
