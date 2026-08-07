# Traspaso — agente RULES / BINS

Cierre de sesión: 07/08/2026. Ámbito: `shared/rules/`, `shared/resources/profiles/`,
`androidApp/inference/bins/`. Milestones M5 (motor de reglas y perfiles) y M6
(escaneo de canecas).

---

## 1. Estado de los milestones

### M5 — Motor de reglas y perfiles: **completo y fusionado**

| Sesión | Issue | PR | Estado |
|---|---|---|---|
| S29 · Motor de reglas material → caneca | #27 | #53 | Fusionado |
| S30 · Carga, validación y catálogo de perfiles | #28 | #58 | Fusionado |
| S31 · Reglas de inspección y reclasificación | #29 | #60 | Fusionado |
| S32 · Restricción a canecas disponibles | #30 | #62 | Fusionado |
| S33 · Segundo país (España + GTC 24) | #31 | #69 | Fusionado |
| Coordinación #54 · pilas y RAEE a punto especial | — | #95 | Fusionado |

### M6 — Escaneo de canecas: **entregado**

| Sesión | Issue | PR | Estado |
|---|---|---|---|
| S34 · Detector de canecas por color y forma | #32 | #74 | Fusionado |
| S35 · Selección, edición manual y persistencia | #33 | **#133** | Abierto, CI verde |

### Pendiente al cerrar

- **PR #133** (S35 · `BinSelection`) — CI verde, esperando fusión de QA. Recrea el
  #76, que la guardia CORE cerró para re-disparar checks y quedó irrecuperable al
  borrarse su rama base. Contenido idéntico, rebasado sobre `main`.
- **PR #134** (regla de inspección de la caja de pizza) — CI verde, esperando fusión.
- **Issue #33 no se cierra con #133**: falta la *pantalla* de confirmación de canecas,
  que es ámbito de FRONT (`androidApp/ui/`). La lógica está lista para conectarse;
  la coordinación quedó comentada en la propia issue.

### Qué hace el módulo, en una línea cada pieza

- `shared/rules/DefaultRuleEngine.kt` — único lugar del sistema que convierte material
  en caneca. Resuelve: regla del material → degradación por contaminación → inspección
  no verificada → restricción a canecas disponibles.
- `shared/rules/profile/` — `ProfileParser` (validación acumulativa con errores
  descriptivos) y `ProfileCatalog` (índice `catalog.json`, modelo **país → institución**).
  Un perfil inválido se rechaza sin tumbar la app y conserva el anterior.
- `shared/rules/bins/` — `ColorRegionFinder` (regiones de color en HSV, robusto a
  iluminación), `BinColorMatcher` (emparejamiento con el perfil activo, descarte
  informado) y `BinSelection` (estado de confirmación del escaneo).
- `shared/resources/profiles/` — `co.json`, `co-gtc24.json`, `es.json`, `catalog.json`,
  `profile.schema.json` y su `README.md`.

### Invariantes que el relevo NO debe romper

1. **Agregar un país es agregar un JSON + su entrada en `catalog.json`.** Si hace falta
   tocar Kotlin, el diseño está mal (RNF-004). S33 lo demuestra: el commit que añade
   España y GTC 24 no toca una línea de código.
2. **Nadie más que `RuleEngine` decide una caneca.** Ni el clasificador, ni la UI, ni los
   repositorios.
3. **Ante la duda no se adivina**: material sin regla, o inspección exigida y no
   verificada → caneca conservadora del perfil, marcada con su `FallbackReason`.
4. **Los destinos `SPECIAL_COLLECTION` están exentos** de la restricción por canecas
   disponibles (un punto posconsumo no es una caneca del entorno) y **se excluyen del
   escaneo**: ni se detectan por color, ni se añaden a mano, ni entran al «omitir escaneo».
5. **Los textos visibles salen del perfil o de recursos de cadenas**, nunca de literales
   en código (RNF-011). El aviso de caneca ausente es plantilla del perfil
   (`unavailableBinNotice`) que el motor renderiza con los nombres visibles.

---

## 2. Auditoría del mapeo de taxonomía (encargo de Juan, hilo completo en la issue #23)

ML tenía dos números en tensión: **top-1 de material subió** 39,3 % → 42,1 % al integrar
Garbage v2, pero el **acierto de caneca bajó** 65,9 % → 61,4 %. La segunda métrica es la
que manda (RNF-008). Audité el mapeo `ml/taxonomy/label_mapping.yaml` y releí las
matrices de `ml/reports/` colapsadas a canecas con el perfil colombiano.

### Método (reproducible)

Colapso material → caneca con el perfil `co`:

```
blanca  = PLASTIC, PAPER, CARDBOARD, BEVERAGE_CARTON, GLASS, METAL
verde   = ORGANIC
negra   = TEXTILE, RESIDUAL
especial= BATTERY, ELECTRONIC          (desde la coordinación #54)
```

Se aplica sobre `confusion_control.csv` de cada corrida y se suman las celdas por
caneca. Mi cálculo reproduce el 65,9 % de ML exacto; su 61,4 % equivale a mi 61,1 %
estricto más 13 muestras de RESIDUAL predichas BATTERY que la convención anterior a #54
contaba como negra.

### Conclusión 1 — el mapeo está bien hecho, con criterio colombiano

No hay mapeos incorrectos por descuido. `Tissues → RESIDUAL` (papel sanitario a la negra,
no a la corriente de papel como haría la intuición anglosajona), `Paper cup →
BEVERAGE_CARTON` y los descartes de EPS y multicapa metalizada son exactamente lo que la
Resolución 2184 exige, y evitan envenenar la corriente blanca.

### Conclusión 2 — la causa raíz del desplome de ruta es la clase `trash` de Garbage v2

`trash → RESIDUAL` es **fiel a la etiqueta de origen y aun así dañino**: esa carpeta está
llena de envases sucios y deformados — visualmente PLASTIC/CARDBOARD/GLASS, o sea caneca
blanca — etiquetados RESIDUAL, o sea negra. El modelo aprende «envase degradado ⇒
RESIDUAL», y como RealWaste es todo dominio degradado, dispara reciclables a la negra.

Masa de error en el control, colapsada a canecas:

| | baseline | full-v2 | Δ |
|---|---|---|---|
| blanca → negra (caro) | 447 | **866** | **+419** |
| blanca acertada | 2637 | 2131 | −506 |
| verde acertada | 124 | 270 | +146 |
| negra acertada | 372 | 503 | +131 |

v2 compró +17 pp de ORGANIC y +15 pp de RESIDUAL pagando entre 24 % y 35 % de cada
corriente blanca hacia la negra (antes 8–27 %). Eso explica **íntegro** el −4,5 pp.

### Conclusión 3 — leída en coste de caneca, la matriz dice algo que en material no se ve

- **Confusiones gratis** (misma caneca): todo el enjambre PLASTIC ↔ METAL ↔ GLASS ↔ PAPER
  ↔ CARDBOARD (blanca) y TEXTILE ↔ RESIDUAL (negra). En el baseline el **44 % de los
  errores de material eran gratis**: por eso convivían top-1 39,3 % y ruta 65,9 %. El
  modelo era mejor de lo que su top-1 sugería.
- **Confusiones caras** (cruzan caneca): cualquier cosa ↔ ORGANIC, blanca ↔ negra, y
  BEVERAGE_CARTON confundido con CARDBOARD (se salta la inspección del vaso: la
  contaminación no se detecta y termina en blanca).
- **v2 mejoró material y empeoró caneca a la vez**: errores totales −134, pero errores
  caros **+229** (1619 → 1848). Caso de libro: PAPER subió 11,4 pp de top-1 y su acierto
  de caneca **bajó** 6 pp.

### Recomendaciones a ML (en orden de valor)

1. **Experimento decisivo y barato**: reentrenar full-v2 **excluyendo solo la carpeta
   `trash` de Garbage v2**. RESIDUAL ya se nutre de TrashNet-trash, RealWaste-Misc y
   TACO (Tissues, Cigarette). Si la ruta recupera ≥65 % conservando la mejora de ORGANIC,
   el diagnóstico queda demostrado. Alternativa: submuestrear `trash` con curación manual.
2. **Optimizar y parar sobre el acierto de ruta del control**, por clase — no sobre top-1.
3. **Pérdida sensible a coste**: matriz de penalización por caneca (confusión intra-blanca
   ≈ 0, cruce blanca ↔ negra/verde alto).
4. **Publicar siempre la matriz colapsada a canecas junto a la de material** en
   `ml/reports/`. Sin ella, las decisiones de entrenamiento se toman a ciegas.

---

## 3. Mapeos pendientes de revisar por muestreo

Protocolo común: muestra aleatoria de **50 recortes por etiqueta**. Regla de oro —
**coste de caneca, no pureza de material**: si ≥ 1/3 de la muestra pertenece visualmente
a **otra caneca**, la etiqueta se descarta (bloque `discards` del YAML, con justificación
al estilo de las de EPS) o se cura.

| Prioridad | Etiqueta | Mapeo actual | Criterio de decisión |
|---|---|---|---|
| **1** | Garbage v2 `trash` | RESIDUAL | Primero el experimento de exclusión (arriba). Si se conserva: eliminar imágenes cuyo objeto dominante sea un envase reconocible |
| 2 | TACO `Garbage bag` | PLASTIC | Si > 1/3 son bolsas llenas o cerradas (basura visual, no material bolsa) → descartar |
| 3 | TACO `Wrapping paper` | PAPER | Si predominan plastificados o metalizados → descartar, mismo criterio que `Plastified paper bag` |
| 4 | TACO `Pizza box` | CARDBOARD | **Se mantiene**: la app ya cubre la contaminación con la regla de inspección (PR #134). Solo verificar que los recortes muestren la caja y no el contenido |
| 5 | Open Images `Coffee cup` | BEVERAGE_CARTON | Curación continua: conservar solo desechables de cartón o papel con recubrimiento; rechazar cerámica, loza, vidrio y termos |

---

## 4. Especificación del mini-set de control propio

**Por qué**: RealWaste no contiene BEVERAGE_CARTON, BATTERY ni ELECTRONIC. Hoy **el
diferenciador del producto — el vaso de café contaminado — es inverificable contra
dominio real**. Este set es de **evaluación exclusivamente, jamás de entrenamiento**
(RNF-016).

**Composición (≈ 400 fotos):**

| Grupo | Fotos | Detalle |
|---|---|---|
| Vasos de café desechables | 120 | 60 limpios / 60 con residuo de bebida; en la mitad de cada grupo, interior visible (encuadre de inspección) |
| Briks (Tetra Pak) | 60 | 30 limpios / 30 con residuo |
| Pilas y baterías | 60 | AA, AAA, botón, de celular; sueltas y en mano |
| Cajas de pizza | 40 | 20 limpias / 20 con grasa y queso — valida la regla del PR #134 |
| Negativos cercanos | 60 | Tazas de cerámica, vasos de plástico rígido, termos: las confusiones que la curación de Open Images debe rechazar |
| Cartón limpio ordinario | 60 | Cajas de envío, plegadizas — contraste contra la caja de pizza |

**Condiciones de captura**: cámara de móvil de gama media o baja, sin filtros ni HDR
forzado; luz interior cálida, exterior y interior tenue (≥ 1/3 del set en luz subóptima);
fondos reales (cocina, escritorio, calle); distancia 20–50 cm; parte del set con la mano
sosteniendo el objeto; encuadre tipo app (objeto centrado, admitiendo corte en bordes).

**Etiquetado**: carpetas `material/estado/` — `beverage_carton/clean`,
`beverage_carton/contaminated`, `battery/`, `cardboard_pizza/clean|contaminated`,
`cardboard_plain/clean`, `negatives/<tipo>` — más un `captures.csv` con archivo,
dispositivo, condición de luz y fondo. Al ser fotos propias del proyecto, declarar su
licencia en `ml/DATA_LICENSES.md`.

**Cómo se reporta**: acierto de **ruta por clase** con el colapso de canecas del perfil
`co`, y para los grupos `contaminated`, tasa de detección de la etapa de contaminación
(S26).

---

## 5. Qué debe saber el relevo

**Operativo**

- Trabaja en **worktree propio**; el checkout principal lo comparten varios agentes y la
  rama activa cambia entre comandos. **Nunca `git add -A`**: añade por rutas explícitas.
- **No hay JDK en la máquina**: las pruebas solo se verifican en CI.
- Si Actions está degradado y un push no genera checks, el respaldo hospedado de QA es
  `gh workflow run ci-respaldo.yml --ref <rama>` — produce el mismo check «Compilar y
  probar» y satisface la protección de rama.
- Cuando `gh pr checks` devuelva vacío, verifica por rama y SHA:
  `gh run list --branch <rama>` contra `git rev-parse origin/<rama>`.
- Publica cada hito en el **tablero de estado #123** (qué / dónde / qué sigue).

**Trampas ya pisadas**

- Kotlin **prohíbe `vararg` de value classes** (`BinId`): usa `BinDefinition` y mapea.
- Fusionar `main` puede romper `gradle/libs.versions.toml` **sin conflicto textual**
  cuando dos agentes arreglan lo mismo en paralelo (se perdió la cabecera `[libraries]`).
  Tras cada merge: `git diff <rama> origin/main -- gradle/libs.versions.toml` debe estar
  vacío si tu rama no debía tocar el catálogo.
- Al añadir una caneca a un perfil, hay pruebas que afirman el número de canecas:
  `ColombiaProfileTest` y `ProfileResourcesTest`. Actualízalas en el mismo commit.

**Coordinaciones abiertas con otros agentes**

- **CAM**: el detector de canecas necesita un frame que implemente `PixelReadableFrame`
  (ARGB submuestreado). El `LumaImageFrame` de S10 solo expone luminancia.
- **FRONT**: pantalla de confirmación de canecas (issue #33) y la clave de recurso nueva
  `inspection.show_box_interior` para la captura dirigida de la caja de pizza.
- **CORE**: `ProfileRepository.setActiveProfile(isoCode)` no puede activar variantes
  institucionales, porque `co` y `co-gtc24` comparten `isoCode`. Propuesta registrada en
  #48: seleccionar por *id de perfil* usando los `ProfileDescriptor` del catálogo. No
  bloquea la v1, que usa los perfiles por defecto.
- **CORE / ScanBinsUseCase**: empareja por hex exacto sobre todos los `profile.bins`;
  convendría que filtrara también los destinos `SPECIAL_COLLECTION`, como ya hacen el
  matcher y `BinSelection`.

**Decisiones de producto vigentes**

- ELECTRONIC entra en v1 con ruta a punto de recolección especial, igual que las pilas.
  **No hay detección automática**: se llega por selección manual o desambiguación de baja
  confianza (CUS-006).
- Frase aprobada del aviso de caneca ausente: «No hay {ideal} disponible; usa {assigned}.»
- La caja de pizza exige inspección del interior, igual que el vaso de café.
