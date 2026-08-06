# -*- coding: utf-8 -*-
"""Genera setup_repo.sh con repo privado, labels, milestones e issues paralelizables."""
import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MILESTONES = [
    ("M0: Fundación y contratos",            "2026-08-14", "Agente CORE — estructura KMP y contratos entre agentes. Único tramo secuencial: desbloquea a todos los demás."),
    ("M1: App shell y design system",        "2026-09-18", "Agente FRONT — CUS-001, CUS-003, CUS-007, CUS-008"),
    ("M2: Cámara y calidad de imagen",       "2026-09-11", "Agente CAM — CUS-004, CUS-005"),
    ("M3: Inferencia on-device y gamas",     "2026-10-02", "Agente EDGE — CUS-003, CUS-005, CUS-008"),
    ("M4: Modelos y datos",                  "2026-10-09", "Agente ML — CUS-003, CUS-005. Camino crítico del proyecto."),
    ("M5: Motor de reglas y perfiles",       "2026-09-04", "Agente RULES — CUS-001, CUS-002, CUS-003, CUS-005"),
    ("M6: Escaneo de canecas",               "2026-09-25", "Agente BINS — CUS-002"),
    ("M7: Persistencia, historial y auth",   "2026-09-04", "Agente DATA — CUS-009, CUS-010"),
    ("M8: Confianza, integración y QA",      "2026-10-23", "Agente QA — CUS-006 y verificación de todos los RNF medibles"),
    ("M9: Preparación iOS y demo",           "2026-10-30", "Agente RELEASE — cierre de la versión 1,0"),
]

AGENTES = {
    "CORE": "0E8A16", "FRONT": "1D76DB", "CAM": "5319E7", "EDGE": "B60205",
    "ML": "D93F0B", "RULES": "0052CC", "BINS": "006B75", "DATA": "5D4037",
    "QA": "FBCA04", "RELEASE": "C2185B",
}

# (sid, agente, milestone_idx, titulo, reqs, cus, objetivo, [tareas], criterio, horas)
S = [
 ("S01","CORE",0,"Estructura Kotlin Multiplatform, version catalog e integración continua",
  "RNF-005, RNF-015","—",
  "El proyecto compila y ejecuta pruebas en integración continua, con los módulos shared y androidApp separados y sin dependencias cruzadas indebidas.",
  ["Crear los módulos `shared` y `androidApp` con el plugin de Kotlin Multiplatform",
   "Configurar el version catalog en `gradle/libs.versions.toml`",
   "Añadir Koin, kotlinx.serialization y SQLDelight al catálogo",
   "Configurar el flujo de integración continua que compila y ejecuta pruebas",
   "Añadir una regla de verificación que falle si aparece un import de plataforma en `shared`"],
  "`./gradlew :androidApp:assembleDebug` y `./gradlew :shared:allTests` pasan en integración continua, y la verificación de imports rechaza cualquier `android.*` dentro de `shared`.", 8),

 ("S02","CORE",0,"Contratos de dominio, puertos y fakes deterministas",
  "RNF-015","—",
  "Las seis interfaces que conectan a los agentes quedan publicadas con implementaciones simuladas deterministas, de modo que ningún agente tenga que esperar a otro.",
  ["Declarar `WasteClassifier`, `BinDetector`, `FrameQualityAnalyzer`, `RuleEngine`, `DeviceTierPolicy` y `AuthProvider`",
   "Declarar las entidades de dominio: `WasteMaterial`, `DisposalRoute`, `Disposal`, `ClassificationResult`, `ContaminationResult`, `FrameQuality`, `DetectedBin`",
   "Implementar un fake determinista de cada puerto en `shared/testing/`",
   "Escribir una prueba por fake que documente su comportamiento esperado"],
  "Cada puerto tiene su fake y su prueba; un agente puede compilar y probar su módulo usando únicamente fakes de los vecinos.", 8),

 ("S03","CORE",0,"Esquema del perfil normativo, taxonomía de materiales y perfil de Colombia",
  "RF-002, RNF-004","CUS-001",
  "Queda definido el formato de los perfiles normativos y publicado el perfil de Colombia conforme a la Resolución 2184 de 2019.",
  ["Definir el esquema JSON del perfil: canecas, reglas por material, reglas de inspección y ruta conservadora",
   "Definir el enumerado `WasteMaterial` como vocabulario compartido entre modelo y motor de reglas",
   "Escribir `co.json` con las canecas blanca, negra y verde y sus reglas",
   "Declarar la regla de inspección del cartón para bebidas con su caneca alternativa por contaminación",
   "Añadir la validación del perfil contra el esquema"],
  "`co.json` valida contra el esquema, cubre las tres canecas de la Resolución 2184 y declara explícitamente el caso del vaso de cartón limpio frente a contaminado.", 8),

 ("S04","FRONT",1,"Design system de estética iOS",
  "RNF-009","CUS-003, CUS-007",
  "Existe un sistema de diseño minimalista de inspiración iOS del que sale todo componente visual de la aplicación.",
  ["Definir la escala tipográfica, la paleta, el espaciado y los radios",
   "Implementar los componentes base: botón, tarjeta, hoja inferior, indicador y píldora de estado",
   "Definir el tema claro y oscuro",
   "Documentar el uso del sistema en `docs/design-system.md`"],
  "Ningún color ni tipografía se declara fuera del design system, verificado por inspección del código de la capa de interfaz.", 10),

 ("S05","FRONT",1,"Navegación y onboarding de selección de país",
  "RF-001, RF-003","CUS-001",
  "El usuario selecciona su país en el primer arranque y puede cambiarlo después desde ajustes, recargando el perfil activo.",
  ["Implementar el grafo de navegación de la aplicación",
   "Construir la pantalla de selección de país a partir del catálogo de perfiles",
   "Conectar la selección al caso de uso de carga de perfil",
   "Implementar el cambio de país desde ajustes con reinicio de canecas disponibles"],
  "El primer arranque solicita país; cambiarlo desde ajustes recarga el perfil y limpia el conjunto de canecas registrado.", 8),

 ("S06","FRONT",1,"Pantalla de cámara con superposiciones e indicaciones",
  "RF-009, RF-013, RF-017","CUS-003, CUS-004",
  "La pantalla principal muestra la vista en vivo con el resultado y las indicaciones de captura sin obstruir la imagen.",
  ["Construir la pantalla de cámara con la vista previa a pantalla completa",
   "Implementar la superposición de resultado con caneca, color y categoría",
   "Implementar la presentación de indicaciones de captura de forma no intrusiva",
   "Conectar la pantalla al caso de uso de clasificación mediante su ViewModel"],
  "La vista en vivo no se bloquea durante el análisis y las indicaciones aparecen y desaparecen sin desplazar el contenido.", 10),

 ("S07","FRONT",1,"Pantalla de resultado con justificación normativa",
  "RF-026, RF-027, RF-028","CUS-007",
  "El usuario puede ver por qué se asignó esa caneca, con la regla aplicada, la referencia normativa y el aviso de carácter orientativo.",
  ["Construir el detalle de la decisión con material, regla y referencia normativa",
   "Indicar cuándo la decisión se degradó por contaminación o por ausencia de la caneca ideal",
   "Mostrar el aviso de carácter orientativo de forma visible",
   "Marcar explícitamente los resultados provenientes de selección manual"],
  "El detalle muestra caneca, color, regla aplicada, norma citada y aviso; un resultado manual se distingue de uno automático.", 6),

 ("S08","FRONT",1,"Ajustes: país, nivel de rendimiento y gestión del historial",
  "RF-003, RF-031, RF-034","CUS-001, CUS-008, CUS-009",
  "El usuario controla desde una sola pantalla el país activo, el nivel de rendimiento y el borrado del historial.",
  ["Construir la pantalla de ajustes",
   "Conectar el selector de país al repositorio de preferencias",
   "Conectar el ajuste de rendimiento a la política de gama, con advertencia del efecto",
   "Implementar el borrado del historial con confirmación explícita"],
  "Los tres ajustes persisten entre reinicios de la aplicación y el borrado del historial requiere confirmación.", 6),

 ("S09","FRONT",1,"Accesibilidad e internacionalización",
  "RNF-010, RNF-011","CUS-007",
  "La interfaz es accesible y está completamente externalizada para admitir idiomas adicionales sin tocar código.",
  ["Extraer todo literal visible a recursos de cadenas",
   "Verificar contraste AA en tema claro y oscuro",
   "Añadir descripciones de contenido para lector de pantalla",
   "Comprobar el comportamiento con tamaños de fuente ampliados",
   "Añadir texto e icono a la comunicación de la caneca, además del color"],
  "Cero literales de texto en el código de interfaz, contraste AA verificado y ninguna información esencial transmitida solo por color.", 8),

 ("S10","CAM",2,"Integración de CameraX y flujo de fotogramas",
  "RF-009","CUS-003",
  "Los fotogramas de la cámara llegan al dominio de forma continua, con gestión correcta del ciclo de vida y sin fugas de memoria.",
  ["Configurar CameraX con caso de uso de previsualización y de análisis de imagen",
   "Implementar la conversión de fotograma al tipo `ImageFrame` del dominio",
   "Gestionar permisos de cámara y el estado de denegación",
   "Liberar recursos correctamente al salir de la pantalla"],
  "Una sesión continua de 5 minutos no incrementa la memoria de forma sostenida y la denegación de permiso se maneja sin cierre inesperado.", 8),

 ("S11","CAM",2,"Métricas de nitidez, luminancia y encuadre",
  "RF-015","CUS-004",
  "El sistema evalúa la calidad de cada fotograma con heurísticas baratas, sin consumir presupuesto de latencia de la clasificación.",
  ["Implementar la varianza del Laplaciano como medida de nitidez",
   "Implementar la luminancia media y la detección de sobreexposición y subexposición",
   "Implementar la comprobación de que el objeto está dentro del área útil",
   "Calibrar los umbrales con un conjunto de fotogramas de prueba"],
  "`FrameQuality` identifica correctamente desenfoque, baja luz y mal encuadre sobre el conjunto de fotogramas de prueba.", 8),

 ("S12","CAM",2,"Detección de suciedad persistente en el lente",
  "RF-016","CUS-004",
  "El sistema distingue una mancha fija en el lente de un objeto estático presente en la escena.",
  ["Implementar la comparación de regiones de baja varianza entre fotogramas consecutivos",
   "Descartar como suciedad las regiones que se desplazan al mover la cámara",
   "Definir el umbral de persistencia que dispara la sugerencia de limpieza"],
  "Una mancha simulada fija se detecta como suciedad y un objeto estático de la escena no genera falso positivo.", 6),

 ("S13","CAM",2,"Motor de indicaciones con política anti-saturación",
  "RF-017, RF-018","CUS-004",
  "El usuario recibe una sola indicación relevante a la vez, con un intervalo mínimo entre ellas, y desaparece al corregirse la condición.",
  ["Implementar la selección de la causa dominante de degradación",
   "Implementar el intervalo mínimo entre indicaciones consecutivas",
   "Retirar la indicación en cuanto la métrica vuelve al rango aceptable",
   "Suprimir las indicaciones cuando la confianza de clasificación ya es suficiente"],
  "Como máximo una indicación cada intervalo mínimo, nunca dos simultáneas, y ninguna cuando todas las métricas son aceptables.", 8),

 ("S14","CAM",2,"Captura dirigida para inspección interior",
  "RF-020","CUS-004, CUS-005",
  "Cuando la regla de inspección lo requiere, el sistema solicita y captura la vista del interior o de la superficie crítica del residuo.",
  ["Implementar el modo de captura dirigida con su indicación en pantalla",
   "Tomar el texto de la solicitud desde el perfil, pasando por recursos de cadenas",
   "Entregar el fotograma dirigido al puerto de clasificación de contaminación",
   "Gestionar el caso en que el usuario no proporciona la toma"],
  "Ante un material con regla de inspección se solicita la vista interior, se captura y se entrega; si el usuario no la da, se aplica la ruta conservadora.", 6),

 ("S15","EDGE",3,"Integración de LiteRT con delegados y respaldo en procesador",
  "RF-011, RF-014","CUS-003",
  "El modelo de clasificación de material se ejecuta en el dispositivo, aprovechando aceleración cuando existe y cayendo a procesador cuando no.",
  ["Integrar el motor LiteRT y la carga de modelos empaquetados",
   "Configurar los delegados NNAPI y GPU con detección de disponibilidad",
   "Implementar el respaldo automático en procesador",
   "Implementar el preprocesamiento de la imagen conforme al modelo",
   "Verificar el funcionamiento en modo avión"],
  "Clasifica correctamente sin conexión y, si el delegado no está disponible, cae a procesador sin fallar ni informar error al usuario.", 12),

 ("S16","EDGE",3,"Detección y recorte del objeto en el encuadre",
  "RF-010","CUS-003",
  "El objeto principal se aísla antes de clasificarlo, con una alternativa sin detector para gama baja.",
  ["Integrar el detector ligero de objeto y el recorte del área de interés",
   "Implementar el marco guía fijo como alternativa para gama baja",
   "Conectar la elección de estrategia a la política de gama",
   "Medir el coste en latencia del detector"],
  "En gama media y alta se recorta el objeto detectado; en gama baja se usa el marco guía y la clasificación sigue funcionando.", 10),

 ("S17","EDGE",3,"Política de gama con micro-benchmark de arranque",
  "RF-029","CUS-008",
  "La aplicación determina la gama real del dispositivo midiendo su latencia efectiva, no solo leyendo sus especificaciones.",
  ["Leer memoria total, número de núcleos, nivel de API y delegados disponibles",
   "Ejecutar N inferencias de calentamiento y medir la latencia media",
   "Combinar las señales en una decisión de gama baja, media o alta",
   "Cachear el resultado y permitir su recálculo si la latencia se degrada"],
  "La gama se resuelve en menos de 2 segundos al arrancar, queda cacheada y se recalcula a la baja si la latencia observada se degrada de forma sostenida.", 8),

 ("S18","EDGE",3,"Activación escalonada de funciones por gama",
  "RF-030, RF-031","CUS-008",
  "Cada función costosa consulta la política de gama antes de activarse, y la clasificación por cámara permanece disponible siempre.",
  ["Implementar la matriz de funciones habilitadas por gama",
   "Conectar cámara, detector y etapa de contaminación a la consulta de gama",
   "Implementar la sobrescritura manual del nivel desde ajustes",
   "Añadir pruebas que verifiquen que la clasificación por cámara está activa en las tres gamas"],
  "La matriz de funciones se respeta en las tres gamas y ninguna combinación deshabilita la clasificación por cámara.", 6),

 ("S19","EDGE",3,"Etapa de contaminación en el dispositivo",
  "RF-021","CUS-005",
  "El segundo modelo evalúa si el residuo aprovechable está limpio o contaminado, sobre el recorte o la toma dirigida.",
  ["Integrar el modelo binario de contaminación",
   "Ejecutarlo sobre el recorte del objeto o sobre la toma dirigida",
   "Devolver el estado con su nivel de confianza",
   "Restringir la ejecución automática según la gama del dispositivo"],
  "Devuelve estado de contaminación con confianza; en gama baja se ejecuta únicamente en captura manual dirigida.", 10),

 ("S20","EDGE",3,"Optimización de latencia, memoria y consumo",
  "RNF-001, RNF-007","CUS-003, CUS-008",
  "El recorrido completo de clasificación cumple el presupuesto de latencia y memoria previsto para cada gama.",
  ["Instrumentar la medición de latencia extremo a extremo",
   "Reutilizar buffers y evitar asignaciones en el bucle de análisis",
   "Ajustar la frecuencia de análisis por gama",
   "Registrar latencia y memoria máxima para las tres gamas"],
  "Latencia y memoria medidas y documentadas por gama; el uso máximo de memoria no supera los 350 MB en clasificación continua.", 12),

 ("S21","ML",4,"Inventario de datasets públicos, licencias y mapeo de taxonomía",
  "RNF-016, RNF-017","CUS-003",
  "Queda documentado qué conjuntos de datos públicos se usan, bajo qué licencia, y cómo se traducen sus etiquetas a la taxonomía del proyecto.",
  ["Inventariar y descargar los conjuntos candidatos: Garbage Dataset v2, Garbage Classification, RealWaste, TrashNet, TACO y ZeroWaste",
   "Verificar y documentar la licencia de cada uno en `ml/DATASETS.md`",
   "Escribir `ml/taxonomy/label_mapping.yaml` con la traducción a `WasteMaterial`",
   "Descartar explícitamente los conjuntos cuya licencia no sea compatible"],
  "`ml/DATASETS.md` y `label_mapping.yaml` están completos, revisados, y toda etiqueta de origen tiene destino o descarte justificado.", 8),

 ("S22","ML",4,"Pipeline de ingesta, unificación y particiones reproducibles",
  "RNF-016","CUS-003",
  "El conjunto de entrenamiento se construye de forma reproducible desde cero, con particiones versionadas y validación cruzada por dataset.",
  ["Implementar la ingesta y normalización de cada conjunto de origen",
   "Aplicar el mapeo de taxonomía y detectar conflictos de etiqueta",
   "Generar particiones con semilla fija, incluida una partición de control de un dataset no visto",
   "Registrar el balance de clases resultante"],
  "Ejecutar el pipeline dos veces desde cero produce particiones idénticas, y existe una partición de control procedente de un dataset excluido del entrenamiento.", 10),

 ("S23","ML",4,"Augmentación orientada al dominio móvil real",
  "RNF-008","CUS-003",
  "El conjunto de entrenamiento refleja las condiciones adversas reales de una cámara de teléfono sostenida a mano.",
  ["Implementar desenfoque gaussiano y de movimiento",
   "Implementar variación de brillo, contraste y temperatura de color",
   "Implementar ruido, artefactos de compresión, oclusión parcial y perspectiva",
   "Revisar visualmente una muestra del resultado y ajustar intensidades"],
  "La muestra revisada es visualmente comparable a fotos reales de teléfono en malas condiciones, sin degradar el objeto hasta hacerlo irreconocible.", 8),

 ("S24","ML",4,"Síntesis de contaminación por segmentación y composición",
  "RF-021","CUS-005",
  "Se genera un conjunto de reciclables contaminados a partir de imágenes de reciclables limpios, ante la inexistencia de conjuntos públicos de este tipo.",
  ["Integrar la segmentación del objeto limpio con U²-Net",
   "Construir una biblioteca de texturas de líquido, grasa y residuo alimenticio",
   "Componer las texturas sobre la superficie del objeto de forma realista",
   "Generar el conjunto sintético y reservar un conjunto de control de contaminación real recopilada de fuentes públicas",
   "Revisar por muestreo la verosimilitud del resultado"],
  "Existe un conjunto sintético de reciclables contaminados y un conjunto de control independiente para evaluar si la síntesis transfiere a suciedad real.", 16),

 ("S25","ML",4,"Entrenamiento del clasificador de material por variante de gama",
  "RF-011, RNF-008","CUS-003",
  "Existen tres variantes del clasificador de material, una por gama de dispositivo, con métricas registradas.",
  ["Entrenar por transferencia desde pesos preentrenados, con ajuste en dos fases",
   "Entrenar MobileNetV3-Small, MobileNetV3-Large 0.75 y EfficientNet-Lite2",
   "Registrar exactitud top-1 de material y acierto de ruta de disposición",
   "Analizar la matriz de confusión y las clases con peor desempeño"],
  "Tres variantes entrenadas con métricas registradas, incluyendo siempre el acierto de ruta además del top-1 de material.", 16),

 ("S26","ML",4,"Entrenamiento del clasificador de contaminación",
  "RF-021","CUS-005",
  "Existe un clasificador binario que distingue reciclables limpios de contaminados, evaluado sobre contaminación real y no solo sintética.",
  ["Entrenar el clasificador binario sobre el conjunto sintético",
   "Evaluar sobre el conjunto de control de contaminación real",
   "Ajustar el umbral de decisión priorizando no clasificar como limpio algo contaminado",
   "Documentar la brecha entre desempeño sintético y real"],
  "El clasificador separa limpio de contaminado y la brecha entre el conjunto sintético y el de control real está medida y documentada.", 10),

 ("S27","ML",4,"Cuantización INT8 y exportación a LiteRT",
  "RNF-001, RNF-006","CUS-003, CUS-008",
  "Los modelos quedan exportados en formato ejecutable en el dispositivo, cuantizados y dentro del presupuesto de tamaño.",
  ["Aplicar cuantización posterior al entrenamiento con conjunto representativo",
   "Exportar las tres variantes de material y la de contaminación a LiteRT",
   "Medir la pérdida de exactitud introducida por la cuantización",
   "Verificar que el tamaño total empaquetado cabe en el presupuesto de la aplicación"],
  "Los cuatro modelos exportados funcionan en el dispositivo, la pérdida por cuantización está documentada y el paquete no supera los 150 MB.", 10),

 ("S28","ML",4,"Evaluación cruzada por dataset y reporte de métricas",
  "RNF-008, RNF-016","CUS-003, CUS-005",
  "Existe evidencia de generalización real, medida sobre datos no vistos durante el entrenamiento.",
  ["Evaluar sobre la partición de control de un dataset excluido del entrenamiento",
   "Reportar exactitud top-1 de material y acierto de ruta de disposición",
   "Reportar el desempeño del recorrido completo sobre reciclables contaminados",
   "Publicar el reporte en `ml/REPORTE_METRICAS.md`"],
  "El reporte publica ambas métricas sobre un conjunto no visto y explicita si se alcanza el 85 % en material y el 95 % en ruta exigidos por RNF-008.", 10),

 ("S29","RULES",5,"Motor de reglas: resolución de material a caneca destino",
  "RF-012","CUS-003",
  "El motor traduce material, estado de contaminación, canecas disponibles y perfil activo en una caneca concreta con su justificación.",
  ["Implementar `RuleEngine` con la resolución material a ruta a caneca",
   "Implementar la generación de la justificación de cada decisión",
   "Escribir la batería de pruebas sobre el perfil de Colombia",
   "Incluir la prueba explícita del vaso de cartón limpio frente a contaminado"],
  "La batería de pruebas pasa sobre el perfil de Colombia, incluido el caso del cartón para bebidas que cambia de caneca según su estado.", 10),

 ("S30","RULES",5,"Carga, validación y extensión del catálogo de perfiles",
  "RF-002, RF-004","CUS-001",
  "Los perfiles se cargan y validan desde recursos locales, y añadir un país no requiere tocar código.",
  ["Implementar la carga de perfiles desde recursos con kotlinx.serialization",
   "Implementar la validación contra el esquema con errores descriptivos",
   "Implementar el catálogo de países disponibles",
   "Añadir una prueba que verifique que un perfil inválido no tumba la aplicación"],
  "Un perfil inválido se rechaza con un error explícito, se conserva el perfil anterior y la aplicación sigue operativa.", 8),

 ("S31","RULES",5,"Reglas de inspección y reclasificación por contaminación",
  "RF-019, RF-022","CUS-005",
  "El perfil declara qué materiales requieren inspección y a qué caneca van si resultan contaminados; el motor lo aplica.",
  ["Implementar la evaluación de reglas de inspección del perfil",
   "Implementar la reasignación a la caneca alternativa por contaminación",
   "Marcar la decisión como degradada por contaminación",
   "Probar el caso en que no se pudo verificar el interior"],
  "Un reciclable detectado como contaminado se reasigna a la caneca de no aprovechables con su justificación, y el caso no verificado aplica la ruta conservadora.", 8),

 ("S32","RULES",5,"Restricción a canecas disponibles con respaldo conservador",
  "RF-008","CUS-002, CUS-003",
  "La recomendación se limita a las canecas que realmente existen en el entorno, con una alternativa razonada cuando falta la ideal.",
  ["Implementar el filtrado de la decisión por el conjunto de canecas disponibles",
   "Implementar la elección de la alternativa más conservadora",
   "Generar el mensaje que explica por qué no se recomendó la caneca ideal",
   "Probar los escenarios de una, dos y tres canecas disponibles"],
  "Ante la ausencia de la caneca ideal se propone la disponible más conservadora y se informa el motivo al usuario.", 8),

 ("S33","RULES",5,"Perfil de un segundo país como prueba de escalabilidad",
  "RNF-004","CUS-001",
  "Se demuestra que incorporar un país nuevo es exclusivamente un trabajo de datos.",
  ["Investigar y documentar el código de colores del segundo país",
   "Escribir su archivo de perfil y registrarlo en el catálogo",
   "Ejecutar la batería de pruebas del motor contra el nuevo perfil",
   "Verificar por inspección que no hubo cambios en código Kotlin"],
  "El segundo país funciona completo sin una sola línea modificada en código Kotlin, verificado en el diff del cambio.", 6),

 ("S34","BINS",6,"Detector de canecas por color y forma",
  "RF-005, RF-006","CUS-002",
  "La aplicación reconoce por cámara qué canecas hay en el entorno y las empareja con las declaradas en el perfil activo.",
  ["Implementar la detección de regiones de color de caneca robusta a iluminación variable",
   "Implementar el emparejamiento con las `BinDefinition` del perfil activo",
   "Descartar colores que no pertenezcan al perfil e informarlo",
   "Evaluar el detector bajo distintas condiciones de luz"],
  "Detecta las canecas del perfil activo bajo iluminación variable y descarta con mensaje los colores ajenos al estándar del país.", 12),

 ("S35","BINS",6,"Confirmación, edición manual y persistencia de las canecas",
  "RF-007","CUS-002",
  "El reconocimiento propone y el usuario decide: la selección final de canecas queda confirmada y persistida.",
  ["Construir la pantalla de confirmación del conjunto reconocido",
   "Permitir añadir y eliminar canecas manualmente desde el perfil",
   "Persistir la selección en el repositorio de disponibilidad",
   "Gestionar el caso en que no se reconoce ninguna caneca"],
  "El usuario confirma, añade o elimina canecas, la selección persiste entre reinicios y omitir el escaneo asume todas las del perfil.", 8),

 ("S36","DATA",7,"Persistencia local con SQLDelight, DataStore y repositorios",
  "RNF-014","CUS-001, CUS-002, CUS-009",
  "La configuración, las canecas disponibles y el historial sobreviven al cierre de la aplicación.",
  ["Definir el esquema de SQLDelight para el historial",
   "Implementar el repositorio de preferencias sobre DataStore",
   "Implementar el repositorio de disponibilidad de canecas",
   "Añadir pruebas de persistencia entre sesiones"],
  "País, canecas disponibles e historial se recuperan correctamente tras cerrar y reabrir la aplicación.", 8),

 ("S37","DATA",7,"Historial local: registro, consulta y borrado",
  "RF-032, RF-033, RF-034","CUS-003, CUS-009",
  "Cada clasificación queda registrada localmente con su resultado, nunca con la imagen, y el usuario puede consultarla y borrarla.",
  ["Implementar el registro del resultado tras cada clasificación",
   "Construir la consulta del historial con material, caneca y fecha",
   "Implementar el borrado completo con confirmación",
   "Añadir una prueba que verifique que ningún fotograma se persiste"],
  "El historial guarda únicamente resultados, la prueba confirma que no se escribe ninguna imagen a disco y el borrado es efectivo.", 8),

 ("S38","DATA",7,"Puerto de autenticación y modo invitado",
  "RF-035, RF-036, RF-037","CUS-010",
  "La infraestructura de inicio de sesión queda preparada para una versión futura, operando mientras tanto en modo invitado.",
  ["Implementar `AuthProvider` con una implementación de invitado",
   "Construir la pantalla de inicio de sesión con la opción de continuar como invitado",
   "Informar que la autenticación con credenciales llegará en una versión futura",
   "Verificar que ninguna función actual exige cuenta"],
  "La aplicación funciona completa en modo invitado y ninguna capa superior depende de un proveedor de autenticación concreto.", 6),

 ("S39","QA",8,"Umbrales de confianza, respuesta conservadora y selección manual",
  "RF-023, RF-024, RF-025","CUS-006",
  "Ante la duda el sistema no adivina: pide otra toma, propone la ruta conservadora o deja elegir al usuario.",
  ["Implementar la comparación con el umbral de confianza y la abstención",
   "Implementar el reintento de toma y la sugerencia conservadora tras duda persistente",
   "Construir la selección manual de categoría",
   "Marcar el resultado manual en el detalle y en el historial",
   "Calibrar el umbral con las métricas del reporte de modelos"],
  "Por debajo del umbral la aplicación nunca emite una caneca como si fuera certera: pide otra toma, sugiere la conservadora explicando por qué, o deja elegir.", 8),

 ("S40","QA",8,"Integración extremo a extremo y pruebas instrumentadas",
  "RNF-013, RNF-015","CUS-001, CUS-002, CUS-003",
  "Los módulos de todos los agentes funcionan juntos sobre dispositivo real, sustituyendo los fakes por implementaciones reales.",
  ["Sustituir los fakes por las implementaciones reales en la configuración de inyección de dependencias",
   "Escribir la prueba instrumentada del recorrido completo de selección de país a resultado",
   "Probar la degradación controlada ante cámara, delegado o modelo no disponibles",
   "Verificar la cobertura de pruebas de la lógica de dominio"],
  "El recorrido completo pasa en dispositivo real, la degradación controlada no cierra la aplicación y la cobertura del dominio alcanza el 70 %.", 12),

 ("S41","QA",8,"Banco de latencia por gama y verificación de requerimientos medibles",
  "RNF-001, RNF-003","CUS-003, CUS-008",
  "Existe evidencia medida de cómo se comporta la aplicación en cada gama de dispositivo.",
  ["Construir el banco de medición de latencia extremo a extremo",
   "Medir en un dispositivo representativo de cada gama",
   "Verificar el funcionamiento desde Android 8.0 y en ARM de 32 y 64 bits",
   "Publicar los resultados en `benchmark/RESULTADOS.md`"],
  "Latencia publicada para las tres gamas y confirmación de que la clasificación por cámara funciona en todas, incluso si no se alcanzan los 2 segundos.", 10),

 ("S42","QA",8,"Verificación de privacidad, modo avión y degradación controlada",
  "RNF-002, RNF-012, RNF-013","CUS-003, CUS-009",
  "Se confirma que la aplicación no envía nada, no guarda imágenes y no se rompe cuando algo falla.",
  ["Ejecutar el recorrido completo en modo avión",
   "Inspeccionar el tráfico de red durante una sesión de clasificación",
   "Verificar que no se escriben imágenes a almacenamiento ni a trazas",
   "Provocar fallos de cámara, delegado y modelo y comprobar la degradación"],
  "Cero tráfico de red durante la clasificación, ningún fotograma escrito a disco o a logs, y ningún cierre inesperado ante fallos provocados.", 6),

 ("S43","RELEASE",9,"Verificación de compilación del componente compartido para iOS",
  "RNF-005","CUS-010",
  "Se confirma que la fase iOS será implementar adaptadores y no reescribir lógica.",
  ["Añadir el target iOS al módulo `shared`",
   "Compilar `shared` para iOS sin modificar código existente",
   "Ejecutar las pruebas del motor de reglas y del dominio en ese target",
   "Documentar qué puertos quedan pendientes de implementación nativa en iOS"],
  "`shared` compila para iOS sin cambios, sus pruebas de dominio y reglas pasan, y la lista de puertos pendientes está documentada.", 8),

 ("S44","RELEASE",9,"Empaquetado de la demo y guion de demostración",
  "RNF-006, RNF-009","CUS-002, CUS-003",
  "La versión 1,0 queda lista para mostrarse, con un recorrido de demostración que exhibe las dos funciones esenciales.",
  ["Generar el APK firmado de la versión 1,0",
   "Verificar el tamaño del paquete instalado",
   "Escribir el guion de demostración que recorre escaneo de canecas y clasificación",
   "Incluir en el guion el caso del vaso de cartón limpio frente a contaminado"],
  "APK instalable dentro del presupuesto de tamaño y guion que demuestra escaneo de canecas, clasificación y el caso del vaso contaminado.", 8),
]

GITIGNORE = """# Gradle
.gradle/
build/
local.properties
captures/

# Android
*.apk
*.aab
*.ap_
*.dex
.cxx/

# IDE
.idea/
*.iml
.kotlin/

# Kotlin Multiplatform / iOS
xcuserdata/
*.xcworkspace/xcuserdata/
Pods/

# Python (pipeline de modelos)
__pycache__/
*.py[cod]
.venv/
venv/
.ipynb_checkpoints/

# Datos y modelos: no se versionan, se reconstruyen con el pipeline
ml/data/
ml/runs/
ml/checkpoints/
*.tflite
*.onnx
*.pt

# Sistema
.DS_Store
Thumbs.db
"""

CODEOWNERS = """# Ámbitos exclusivos por agente. Escribir fuera del propio ámbito
# requiere una issue de coordinación explícita.

/gradle/                    @BotaBien/core
/shared/domain/port/        @BotaBien/core
/shared/testing/            @BotaBien/core
/androidApp/ui/             @BotaBien/front
/androidApp/camera/         @BotaBien/cam
/androidApp/inference/      @BotaBien/edge
/androidApp/inference/bins/ @BotaBien/bins
/ml/                        @BotaBien/ml
/shared/rules/              @BotaBien/rules
/shared/resources/profiles/ @BotaBien/rules
/shared/data/               @BotaBien/data
/benchmark/                 @BotaBien/qa
/iosApp/                    @BotaBien/release
/docs/                      @juanurrego
/plan/                      @juanurrego
"""

AGENTS_MD = """# Instrucciones para agentes

Lee `context-for-vibe-coding.md` antes de escribir una sola línea de código.
Contiene las reglas obligatorias del proyecto: stack, convenciones, estructura de
módulos, invariantes de arquitectura, contratos entre agentes y definición de "hecho".

Antes de empezar una issue:

1. Lee `context-for-vibe-coding.md` completo.
2. Lee `docs/arquitectura.md` para entender dónde encaja tu módulo.
3. Comprueba en `plan/plan_de_trabajo.md` cuál es tu ámbito de archivos exclusivo.
4. Implementa exactamente los RF y CUS que cita la issue: ni una feature de más.
5. Trabaja contra los fakes de `shared/testing/` si tu módulo depende de otro agente.

No modifiques archivos fuera de tu ámbito sin una issue de coordinación.
"""


def sh_escape(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("`", "\\`")


def build():
    L = []
    a = L.append
    a("#!/usr/bin/env bash")
    a("# Crea el repositorio privado BotaBien en GitHub con labels, milestones e issues.")
    a("# Requisitos: gh instalado y autenticado (gh auth login), git configurado.")
    a("# Uso: cd BotaBien && bash setup_repo.sh")
    a("set -euo pipefail")
    a("")
    a('REPO_NAME="BotaBien"')
    a('DESC="Clasificacion de residuos por camara con redes neuronales en el dispositivo, segun la norma de cada pais"')
    a("")
    a('if ! command -v gh >/dev/null 2>&1; then')
    a('  echo "ERROR: GitHub CLI (gh) no esta instalado."; exit 1')
    a('fi')
    a('gh auth status >/dev/null 2>&1 || { echo "ERROR: ejecuta primero: gh auth login"; exit 1; }')
    a('cd "$(dirname "$0")"')
    a('[ -f "README.md" ] || { echo "ERROR: ejecuta el script desde dentro de la carpeta BotaBien."; exit 1; }')
    a("")
    a('echo "==> 1/5 Creando repositorio privado"')
    a('git init -b main 2>/dev/null || true')
    a('git add -A')
    a('git commit -m "docs: especificacion inicial, arquitectura y plan de trabajo" 2>/dev/null || true')
    a('gh repo create "$REPO_NAME" --private --source=. --description "$DESC" --push \\')
    a('  || { echo "El repositorio ya existe; haciendo push."; git push -u origin main; }')
    a("")
    a('OWNER=$(gh repo view --json owner --jq .owner.login)')
    a('REPO="$OWNER/$REPO_NAME"')
    a('echo "Repositorio: https://github.com/$REPO"')
    a("")
    a('echo "==> 2/5 Creando labels"')
    a('gh label create "RF" --color 1D76DB --description "Implementa requerimiento funcional" 2>/dev/null || true')
    a('gh label create "RNF" --color 5319E7 --description "Requerimiento no funcional" 2>/dev/null || true')
    a('gh label create "docs" --color 0E8A16 --description "Documentacion" 2>/dev/null || true')
    a('gh label create "bug" --color D73A4A --description "Defecto" 2>/dev/null || true')
    a('gh label create "camino-critico" --color B60205 --description "Bloquea la fecha de entrega" 2>/dev/null || true')
    for ag, color in AGENTES.items():
        a('gh label create "agente:%s" --color %s --description "Workstream del agente %s" 2>/dev/null || true' % (ag, color, ag))
    a("")
    a('echo "==> 3/5 Creando milestones"')
    for i, (title, due, desc) in enumerate(MILESTONES):
        a('MS%d=$(gh api repos/$REPO/milestones -f title="%s" -f due_on="%sT23:59:59Z" -f description="%s" --jq .title 2>/dev/null || echo "%s")'
          % (i, sh_escape(title), due, sh_escape(desc), sh_escape(title)))
    a("")
    a('echo "==> 4/5 Creando issues (una por sesion de trabajo del plan)"')
    for (sid, agente, ms_idx, titulo, reqs, cus, objetivo, tareas, criterio, horas) in S:
        ms_title = MILESTONES[ms_idx][0]
        labels = ["agente:%s" % agente]
        labels.append("RNF" if reqs.strip().startswith("RNF") else "RF")
        if agente == "ML" or sid in ("S01", "S02", "S03"):
            labels.append("camino-critico")
        body = []
        body.append("## Objetivo de la sesión")
        body.append(objetivo)
        body.append("")
        body.append("## Requerimientos que implementa")
        body.append("- Requerimientos: %s" % reqs)
        body.append("- Casos de uso relacionados: %s" % cus)
        body.append("")
        body.append("## Tareas")
        for t in tareas:
            body.append("- [ ] %s" % t)
        body.append("")
        body.append("## Criterio de hecho")
        body.append(criterio)
        body.append("")
        body.append("## Estimación")
        body.append("%d horas — agente %s, hito «%s», según plan/plan_de_trabajo.md" % (horas, agente, ms_title))
        body.append("")
        body.append("## Antes de empezar")
        body.append("Lee `context-for-vibe-coding.md`. Trabaja solo dentro del ámbito de archivos del agente %s "
                    "y usa los fakes de `shared/testing/` para los módulos de otros agentes." % agente)
        a("")
        a('gh issue create \\')
        a('  --title "%s · %s" \\' % (sid, sh_escape(titulo)))
        a('  --milestone "%s" \\' % sh_escape(ms_title))
        for lb in labels:
            a('  --label "%s" \\' % lb)
        a("  --body \"$(cat <<'BODY'")
        for line in body:
            a(line)
        a("BODY")
        a('  )" || echo "AVISO: fallo la issue %s"' % sid)
    a("")
    a('echo "==> 5/5 Listo"')
    a('echo "Repositorio:  https://github.com/$REPO"')
    a('echo "Milestones:   %d"' % len(MILESTONES))
    a('echo "Issues:       %d"' % len(S))
    a('echo "Tablero:      https://github.com/$REPO/issues"')
    return "\n".join(L) + "\n"


os.makedirs(BASE, exist_ok=True)
with open(os.path.join(BASE, "setup_repo.sh"), "w", encoding="utf-8") as f:
    f.write(build())
with open(os.path.join(BASE, ".gitignore"), "w", encoding="utf-8") as f:
    f.write(GITIGNORE)
with open(os.path.join(BASE, "CODEOWNERS"), "w", encoding="utf-8") as f:
    f.write(CODEOWNERS)
with open(os.path.join(BASE, "AGENTS.md"), "w", encoding="utf-8") as f:
    f.write(AGENTS_MD)
with open(os.path.join(BASE, "CLAUDE.md"), "w", encoding="utf-8") as f:
    f.write(AGENTS_MD)

print("setup_repo.sh generado")
print("milestones:", len(MILESTONES), "| issues:", len(S))
horas = sum(x[9] for x in S)
print("horas base en issues:", horas)
from collections import Counter
print("por agente:", dict(Counter(x[1] for x in S)))
