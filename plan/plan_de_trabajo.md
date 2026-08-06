# Plan de trabajo — BotaBien

Fecha de elaboración: 06/08/2026 · Responsable: Juan Urrego · Versión 1,0

## Modelo de ejecución

El desarrollo lo ejecutan **agentes de IA trabajando en paralelo**, no personas. Cada milestone corresponde a un *workstream* con un agente responsable y un ámbito de archivos propio. Juan Urrego dirige, revisa e integra.

La partición está diseñada para que ocho agentes puedan avanzar concurrentemente desde la semana 2 sin bloquearse. La condición que lo hace posible es el milestone **M0**, único tramo secuencial del plan: fija la estructura de módulos y, sobre todo, los **contratos de interfaces** entre agentes. Una vez publicados esos contratos junto a sus *fakes* deterministas en `shared/testing/`, cada agente trabaja contra la interfaz del vecino sin esperar su implementación real.

| Agente | Workstream | Ámbito de archivos exclusivo |
|---|---|---|
| CORE | M0 — Fundación y contratos | raíz de Gradle, `shared/domain/port/`, `shared/testing/` |
| FRONT | M1 — App shell y design system | `androidApp/ui/` |
| CAM | M2 — Cámara y calidad de imagen | `androidApp/camera/` |
| EDGE | M3 — Inferencia on-device y gamas | `androidApp/inference/` |
| ML | M4 — Modelos y datos | `ml/` |
| RULES | M5 — Motor de reglas y perfiles | `shared/rules/`, `shared/resources/profiles/` |
| BINS | M6 — Escaneo de canecas | `androidApp/inference/bins/`, `ml/bins/` |
| DATA | M7 — Persistencia, historial y auth | `shared/data/` |
| QA | M8 — Confianza, integración y verificación | `androidApp/test/`, `shared/test/`, `benchmark/` |
| RELEASE | M9 — Preparación iOS y demo | `iosApp/`, `release/` |

**Regla anticolisión:** cada agente escribe únicamente dentro de su ámbito. Tocar el ámbito de otro requiere una issue de coordinación explícita. El archivo `CODEOWNERS` refleja esta partición. Una rama por issue, con el patrón `<agente>/S<NN>-<slug>`.

## Estimación

Complejidad asignada a cada requerimiento: S de 2 a 4 horas, M de 4 a 8, L de 8 a 16. Los requerimientos no funcionales transversales —rendimiento por gama, accesibilidad, internacionalización, privacidad, reproducibilidad del entrenamiento— se estiman como sesiones propias porque no se resuelven "de paso".

| Workstream | Sesiones | Horas base |
|---|---|---|
| M0 Fundación y contratos | 3 | 24 |
| M1 App shell y design system | 6 | 48 |
| M2 Cámara y calidad de imagen | 5 | 36 |
| M3 Inferencia y gamas | 6 | 58 |
| M4 Modelos y datos | 8 | 88 |
| M5 Motor de reglas y perfiles | 5 | 40 |
| M6 Escaneo de canecas | 2 | 20 |
| M7 Persistencia, historial y auth | 3 | 22 |
| M8 Confianza, integración y QA | 4 | 36 |
| M9 Preparación iOS y demo | 2 | 16 |
| **Total base** | **44** | **388** |

Sobre las 388 horas base se aplica un **margen del 50 %**, no del 30 % habitual, por tres razones concretas: el stack Kotlin Multiplatform es nuevo para el proyecto, la calidad final del modelo depende de datasets públicos que habrá que descartar y sustituir sobre la marcha, y la síntesis de contaminación es una técnica sin implementación de referencia disponible.

> **Esfuerzo total estimado: ~580 horas-agente.**

### Traducción a calendario

Las horas-agente no se convierten linealmente en calendario porque ocho workstreams corren a la vez. El factor limitante real es la capacidad de revisión e integración de Juan, estimada en 12 a 15 horas por semana.

El **camino crítico** es M0 → M4 → M3 → M8 → M9: los modelos gobiernan el cronograma. M4 son 88 horas base, 132 con margen, y no puede empezar antes de que M0 fije la taxonomía de materiales. Todo lo demás cabe holgadamente en paralelo por debajo de ese camino.

> **Duración estimada: ~580 horas-agente ≈ 12 semanas de calendario, con revisión de 12 a 15 h/semana.**
> **Inicio 06/08/2026 · cierre previsto 30/10/2026.**

Si prefieres comprimir a 8 semanas hay que recortar alcance —los candidatos naturales son el perfil del segundo país (S33), el historial local (M7) y la preparación de iOS (M9)— o subir la dedicación de revisión a más de 20 h/semana, que es lo que realmente destraba el paralelismo. Si prefieres alargar a 16 semanas, el margen sube al 70 % y M4 gana espacio para iterar el modelo, que es donde más rendimiento tiene el tiempo extra. Dilo y recalculo el cronograma y las fechas de los milestones.

## Milestones

| ID | Milestone | Agente | Fecha límite | Depende de |
|---|---|---|---|---|
| M0 | Fundación y contratos | CORE | 14/08/2026 | — |
| M5 | Motor de reglas y perfiles | RULES | 04/09/2026 | M0 |
| M7 | Persistencia, historial y auth | DATA | 04/09/2026 | M0 |
| M2 | Cámara y calidad de imagen | CAM | 11/09/2026 | M0 |
| M1 | App shell y design system | FRONT | 18/09/2026 | M0 |
| M6 | Escaneo de canecas | BINS | 25/09/2026 | M0, M5 |
| M3 | Inferencia on-device y gamas | EDGE | 02/10/2026 | M0 |
| M4 | Modelos y datos | ML | 09/10/2026 | M0 |
| M8 | Confianza, integración y QA | QA | 23/10/2026 | M1–M7 |
| M9 | Preparación iOS y demo | RELEASE | 30/10/2026 | M8 |

## Sesiones de trabajo

Cada sesión es una issue del repositorio. El criterio de hecho es la condición verificable que la cierra.

### M0 — Fundación y contratos · CORE

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S01 | Estructura Kotlin Multiplatform, version catalog, CI de compilación y pruebas | RNF-005, RNF-015 | 8 | `./gradlew :androidApp:assembleDebug` y `:shared:allTests` pasan en CI |
| S02 | Contratos de dominio, puertos y *fakes* deterministas en `shared/testing/` | RNF-015 | 8 | Las seis interfaces del contrato existen con *fake* y prueba propia; ningún import de plataforma en `shared/` |
| S03 | Esquema del perfil normativo, taxonomía `WasteMaterial` y perfil de Colombia | RF-002, RNF-004 | 8 | El perfil `co.json` valida contra el esquema y cubre las tres canecas de la Resolución 2184 |

### M1 — App shell y design system · FRONT

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S04 | Design system de estética iOS: tipografía, paleta, espaciado, componentes base | RNF-009 | 10 | Ningún color ni tipografía se declara fuera del design system |
| S05 | Navegación y onboarding de selección de país | RF-001, RF-003 | 8 | Primer arranque pide país; el cambio desde ajustes recarga el perfil activo |
| S06 | Pantalla de cámara con superposiciones e indicaciones | RF-009, RF-013, RF-017 | 10 | La vista en vivo muestra resultado e indicaciones sin bloquear el frame |
| S07 | Pantalla de resultado con justificación normativa y aviso orientativo | RF-026, RF-027, RF-028 | 6 | El resultado muestra caneca, color, regla aplicada, norma citada y disclaimer |
| S08 | Ajustes: país, nivel de rendimiento y gestión del historial | RF-003, RF-031, RF-034 | 6 | Los tres ajustes persisten entre reinicios |
| S09 | Accesibilidad e internacionalización | RNF-010, RNF-011 | 8 | Cero literales en código; contraste AA; la caneca se comunica también por texto e icono, no solo por color |

### M2 — Cámara y calidad de imagen · CAM

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S10 | Integración de CameraX y flujo de frames hacia el dominio | RF-009 | 8 | Frames llegan al analizador sin fugas de memoria en sesión de 5 minutos |
| S11 | Métricas de nitidez, luminancia y encuadre | RF-015 | 8 | `FrameQuality` detecta correctamente desenfoque y baja luz en el set de prueba |
| S12 | Detección de suciedad persistente del lente | RF-016 | 6 | Una mancha fija entre frames se detecta y no se confunde con un objeto estático |
| S13 | Motor de indicaciones con política anti-saturación | RF-017, RF-018 | 8 | Como máximo una indicación cada N segundos y solo si la confianza está bajo umbral |
| S14 | Captura dirigida para inspección interior | RF-020 | 6 | Al detectarse un material con regla de inspección, se solicita la vista interior y se captura |

### M3 — Inferencia on-device y gamas · EDGE

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S15 | Integración de LiteRT con delegados NNAPI y GPU, y respaldo en CPU | RF-011, RF-014 | 12 | Clasifica en modo avión y cae a CPU sin fallar si el delegado no está disponible |
| S16 | Detección y recorte del objeto en el encuadre | RF-010 | 10 | En gama media y alta se recorta el objeto; en gama baja se usa el marco guía fijo |
| S17 | `DeviceTierPolicy` con micro-benchmark de arranque | RF-029 | 8 | El tier se resuelve en menos de 2 s al arrancar y queda cacheado |
| S18 | Activación escalonada de funciones y ajuste manual | RF-030, RF-031 | 6 | La matriz de funciones por gama se respeta; la clasificación por cámara funciona en las tres gamas |
| S19 | Etapa de contaminación en el dispositivo | RF-021 | 10 | El segundo modelo corre sobre el recorte y devuelve estado con confianza |
| S20 | Optimización de latencia, memoria y consumo | RNF-001, RNF-007 | 12 | Latencia y memoria medidas y registradas para las tres gamas |

### M4 — Modelos y datos · ML

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S21 | Inventario de datasets públicos, licencias y mapeo de taxonomía | RNF-016, RNF-017 | 8 | `ml/DATASETS.md` y `label_mapping.yaml` completos y revisados |
| S22 | Pipeline de ingesta, unificación y particiones reproducibles | RNF-016 | 10 | El pipeline se reproduce desde cero con semilla fija y produce las mismas particiones |
| S23 | Augmentación orientada al dominio móvil real | RNF-008 | 8 | Desenfoque, movimiento, luz, ruido, artefactos JPEG y oclusión aplicados y verificados visualmente |
| S24 | Síntesis de contaminación por segmentación y composición | RF-021 | 16 | Conjunto sintético de reciclables contaminados generado y revisado por muestreo |
| S25 | Entrenamiento del clasificador de material por variante de gama | RF-011, RNF-008 | 16 | Tres variantes entrenadas con métricas registradas |
| S26 | Entrenamiento del clasificador de contaminación | RF-021 | 10 | Clasificador binario con separación clara entre limpio y contaminado |
| S27 | Cuantización INT8 y exportación a LiteRT por gama | RNF-001, RNF-006 | 10 | Los tres modelos exportados cumplen el presupuesto de tamaño de la app |
| S28 | Evaluación cruzada por dataset y reporte de métricas | RNF-008, RNF-016 | 10 | Reporte con top-1 de material y acierto de ruta sobre un dataset no visto |

### M5 — Motor de reglas y perfiles · RULES

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S29 | `RuleEngine`: resolución material, contaminación y ruta a caneca | RF-012 | 10 | Batería de pruebas sobre el perfil de Colombia, incluido el caso del vaso con recubrimiento |
| S30 | Carga, validación y extensión del catálogo de perfiles | RF-002, RF-004 | 8 | Un perfil inválido se rechaza con error explícito y no tumba la app |
| S31 | Reglas de inspección y reclasificación por contaminación | RF-019, RF-022 | 8 | Un reciclable contaminado se reasigna a la caneca de no aprovechables con justificación |
| S32 | Restricción a canecas disponibles con respaldo conservador | RF-008 | 8 | Si falta la caneca ideal se propone la disponible más conservadora y se informa el motivo |
| S33 | Perfil de un segundo país como prueba de escalabilidad | RNF-004 | 6 | El segundo país funciona sin un solo cambio en código Kotlin |

### M6 — Escaneo de canecas · BINS

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S34 | Detector de canecas por color y forma | RF-005, RF-006 | 12 | Detecta las canecas del perfil activo bajo iluminación variable |
| S35 | Emparejamiento con el perfil, confirmación y edición manual | RF-007 | 8 | El usuario confirma, añade o elimina canecas y la selección persiste |

### M7 — Persistencia, historial y auth · DATA

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S36 | SQLDelight, DataStore y repositorios del dominio | RNF-014 | 8 | Configuración e historial sobreviven al cierre de la aplicación |
| S37 | Historial local: registro, consulta y borrado | RF-032, RF-033, RF-034 | 8 | Se registra el resultado y nunca el frame; el borrado es efectivo |
| S38 | Puerto de autenticación con modo invitado | RF-035, RF-036, RF-037 | 6 | La pantalla de sesión existe, `AuthProvider` devuelve invitado y la app funciona sin cuenta |

### M8 — Confianza, integración y QA · QA

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S39 | Umbrales de confianza, respuesta conservadora y selección manual | RF-023, RF-024, RF-025 | 8 | Bajo el umbral la app no adivina: pide otra toma o deja elegir al usuario |
| S40 | Integración extremo a extremo y pruebas instrumentadas | RNF-013, RNF-015 | 12 | El recorrido completo, de selección de país a resultado, pasa en dispositivo real |
| S41 | Banco de latencia por gama y verificación de requerimientos medibles | RNF-001, RNF-003 | 10 | Latencia medida y publicada para las tres gamas; la clasificación funciona en todas |
| S42 | Verificación de privacidad, modo avión y degradación controlada | RNF-002, RNF-012, RNF-013 | 6 | Sin tráfico de red durante la clasificación; ningún frame escrito a disco ni a logs |

### M9 — Preparación iOS y demo · RELEASE

| Sesión | Objetivo | Requerimientos | Horas | Criterio de hecho |
|---|---|---|---|---|
| S43 | Verificación de compilación de `shared` para target iOS | RNF-005 | 8 | `shared` compila para iOS sin cambios y el motor de reglas pasa sus pruebas ahí |
| S44 | Empaquetado de la demo, APK firmado y guion de demostración | RNF-006, RNF-009 | 8 | APK instalable y guion que recorre las dos funciones principales |

## Cronograma

| Semana | Fechas | CORE | FRONT | CAM | EDGE | ML | RULES | BINS | DATA | QA | RELEASE |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 06–14 ago | S01 S02 S03 | | | | | | | | | |
| 2 | 17–21 ago | | S04 | S10 | S15 | S21 | S29 | | S36 | | |
| 3 | 24–28 ago | | S04 S05 | S11 | S15 S16 | S22 | S29 S30 | | S37 | | |
| 4 | 31 ago–04 sep | | S05 S06 | S12 | S16 S17 | S23 | S31 S32 | | S38 ✔ | | |
| 5 | 07–11 sep | | S06 | S13 S14 ✔ | S18 | S24 | S33 ✔ | | | | |
| 6 | 14–18 sep | | S07 S08 S09 ✔ | | S19 | S24 S25 | | S34 | | | |
| 7 | 21–25 sep | | | | S19 S20 | S25 | | S34 S35 ✔ | | | |
| 8 | 28 sep–02 oct | | | | S20 ✔ | S26 | | | | S39 | |
| 9 | 05–09 oct | | | | | S27 S28 ✔ | | | | S39 | |
| 10 | 12–16 oct | | | | | | | | | S40 | |
| 11 | 19–23 oct | | | | | | | | | S41 S42 ✔ | |
| 12 | 26–30 oct | | | | | | | | | | S43 S44 ✔ |

El símbolo ✔ marca el cierre del milestone correspondiente.

## Riesgos del plan

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Los datasets públicos no generalizan a fotos de móvil reales | Alto — la app acierta en el laboratorio y falla en la mano | Validación cruzada por dataset desde S22, augmentación agresiva en S23 y RealWaste como conjunto de control por venir de un entorno real |
| La contaminación sintética no transfiere a suciedad real | Alto — se cae el diferenciador del producto | S24 se evalúa con un conjunto de control aparte; si no transfiere, el plan B es reducir la etapa 2 a una pregunta explícita al usuario, conservando el flujo de UX y la utilidad |
| Latencia inaceptable en gama baja | Medio | La política de gamas ya contempla clasificación bajo demanda en lugar de continua; el requisito de los 2 s es objetivo, no bloqueo |
| Los contratos de M0 resultan insuficientes y hay que cambiarlos | Medio — bloquea a varios agentes a la vez | Los *fakes* de S02 fuerzan a probar los contratos antes de que nadie dependa de ellos; todo cambio posterior pasa por issue de coordinación |
| El detector de canecas confunde colores bajo iluminación variable | Medio | Confirmación manual obligatoria en S35: el reconocimiento propone, el usuario decide |
| Colisiones entre agentes sobre los mismos archivos | Medio | Ámbitos exclusivos por agente, `CODEOWNERS` y una rama por issue |
