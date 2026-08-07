# -*- coding: utf-8 -*-
"""Datos del documento de requerimientos de BotaBien."""

FECHA = "06/08/2026"
RESPONSABLE = "Juan Urrego"
PROYECTO = "BotaBien"

EQUIPO = [
    ("Gestor de Proyectos", "Juan Urrego"),
    ("Analista de requerimientos", "Juan Urrego"),
    ("Arquitecto de software", "Juan Urrego"),
    ("Programadores / Desarrolladores", "Agentes de IA en ejecución paralela, dirigidos por Juan Urrego"),
    ("Ingeniero de aprendizaje automático", "Agente ML, dirigido por Juan Urrego"),
    ("Tester / QA", "Agente QA, dirigido por Juan Urrego"),
    ("Responsable de revisión y aprobación", "Juan Urrego"),
]

VERSIONES = [
    (FECHA, "1,0", "Creación de documento de requerimientos", RESPONSABLE),
]

DEFINICIONES = [
    ("CNN (Red Neuronal Convolucional)", "arquitectura de red neuronal especializada en el análisis de imágenes, base de la clasificación visual de residuos de este proyecto."),
    ("Inferencia en el dispositivo (on-device)", "ejecución del modelo de aprendizaje automático en el propio teléfono, sin enviar datos a un servidor."),
    ("LiteRT", "motor de ejecución de modelos de aprendizaje automático en dispositivos móviles, sucesor de TensorFlow Lite."),
    ("Cuantización INT8", "técnica que reduce la precisión numérica de un modelo a enteros de 8 bits para acelerarlo y reducir su tamaño, con una pérdida mínima de exactitud."),
    ("Delegado de aceleración", "componente que traslada el cálculo del modelo a hardware especializado del dispositivo, como la GPU o la NPU."),
    ("NNAPI (Neural Networks API)", "interfaz de Android que permite a las aplicaciones aprovechar los aceleradores de aprendizaje automático del dispositivo."),
    ("Gama del dispositivo (tier)", "clasificación en baja, media o alta que la aplicación asigna al teléfono según su capacidad real de cómputo, y que determina qué funciones se habilitan."),
    ("Perfil normativo", "archivo de datos que declara, para un país concreto, las canecas existentes, sus colores y las reglas que asignan cada material a una caneca."),
    ("Ruta de disposición", "destino final del residuo con independencia del color de la caneca: aprovechable, no aprovechable, orgánico, peligroso o recolección especial."),
    ("Motor de reglas", "componente que traduce el material identificado por el modelo a una caneca concreta, aplicando el perfil normativo activo."),
    ("Contaminación de un reciclable", "presencia de residuo de alimento, líquido o grasa que impide que un material aprovechable pueda reciclarse, obligando a enviarlo a la caneca de no aprovechables."),
    ("Resolución 2184 de 2019", "norma colombiana que unifica el código de colores para la separación de residuos en la fuente en blanco, negro y verde, vigente desde el 1 de enero de 2021."),
    ("KMP (Kotlin Multiplatform)", "tecnología que permite compartir un mismo código de lógica de negocio entre Android e iOS."),
    ("Puerto (port)", "interfaz declarada en la capa de dominio que abstrae una capacidad de plataforma y permite implementarla de forma distinta en cada sistema operativo."),
    ("Augmentación de datos", "generación de variantes artificiales de las imágenes de entrenamiento para que el modelo tolere condiciones reales adversas."),
    ("Contaminación sintética", "generación artificial de imágenes de reciclables sucios a partir de imágenes de reciclables limpios, ante la inexistencia de conjuntos de datos públicos de este tipo."),
    ("CUS (Caso de Uso)", "interacción completa entre un actor y el sistema."),
    ("RF (Requerimiento Funcional)", "capacidad concreta y verificable que ofrece el sistema."),
    ("RNF (Requerimiento No Funcional)", "restricción de calidad que el sistema debe satisfacer."),
]

JUSTIFICACION = (
    "La separación de residuos en la fuente falla en la práctica por dos motivos distintos. El primero es de "
    "conocimiento: la mayoría de personas no sabe con certeza a qué caneca corresponde cada material, y el código "
    "de colores cambia entre países e incluso entre instituciones de un mismo país. El segundo es más sutil y "
    "explica buena parte de los errores: las reglas reales dependen del estado del residuo, no solo de su material. "
    "Un vaso de cartón para bebidas aparenta ser papel aprovechable, pero lleva un recubrimiento de polietileno y "
    "suele conservar residuo líquido en su interior; la Resolución 2184 de 2019 exige explícitamente que lo depositado "
    "en la caneca blanca esté limpio y seco, de modo que ese vaso corresponde a la caneca negra. Esa condición es "
    "invisible desde el exterior del objeto y ninguna aplicación existente la verifica.\n\n"
    "BotaBien nace para resolver ambos problemas a la vez. Mediante la cámara del teléfono y redes neuronales que se "
    "ejecutan íntegramente en el dispositivo, identifica el material del residuo, comprueba si está contaminado "
    "solicitando al usuario la toma que hace falta, y traduce ese diagnóstico a la caneca correcta según la norma "
    "vigente del país en el que se encuentra. La traducción no está cableada en el código sino declarada en un perfil "
    "normativo intercambiable, lo que permite incorporar nuevos países sin reentrenar los modelos ni modificar la "
    "aplicación.\n\n"
    "El sistema está dirigido a cualquier persona que deba separar un residuo y dude sobre su destino, y está diseñado "
    "para funcionar sin conexión a internet y en dispositivos de cualquier gama, porque el problema que aborda es más "
    "frecuente precisamente donde los recursos tecnológicos son más limitados. La primera versión se entrega para "
    "Android como demostración funcional; su arquitectura está preparada desde el inicio para portarse a iOS y, en una "
    "fase muy posterior, para operar sobre cámaras fijas instaladas en puntos de disposición."
)

DOC_RELACIONADA = [
    ("Resolución 2184 de 2019 — Código de colores para la separación de residuos en Colombia", "Ministerio de Ambiente y Desarrollo Sostenible"),
    ("Abecé del código de colores para la separación de residuos", "Ministerio de Vivienda, Ciudad y Territorio"),
    ("Arquitectura y diagramas del sistema", "docs/arquitectura.md del repositorio"),
    ("Contexto unico del proyecto", "CONTEXTO.md del repositorio"),
    ("Plan de trabajo y cronograma", "plan/plan_de_trabajo.md del repositorio"),
]

FLUJO_PROCESO = [
    "El usuario abre la aplicación.",
    "El sistema determina la gama del dispositivo y habilita las funciones correspondientes.",
    "El sistema solicita el país y carga el perfil normativo correspondiente.",
    "El usuario escanea con la cámara las canecas disponibles en su entorno, o acepta las que declara el perfil.",
    "El usuario apunta la cámara al residuo que desea clasificar.",
    "El sistema evalúa la calidad de la imagen y, si es necesario, indica al usuario cómo corregir la toma.",
    "El sistema detecta y recorta el objeto, y clasifica su material con el modelo local.",
    "Si el material requiere inspección, el sistema solicita una toma dirigida y evalúa la contaminación.",
    "El sistema consulta el motor de reglas con el material, la contaminación, las canecas disponibles y el perfil activo.",
    "Si la confianza es insuficiente, el sistema pide otra toma o permite la selección manual, sin adivinar.",
    "El sistema muestra la caneca destino con su color, la regla aplicada y la referencia normativa.",
    "El sistema registra el resultado en el historial local, sin almacenar la imagen.",
]

# (id, nombre, actor, descripcion, [flujo principal], [flujos alternativos])
CASOS_USO = [
    ("CUS-001", "Configurar país y perfil de clasificación", "Usuario",
     "El usuario selecciona el país en el que se encuentra para que la aplicación cargue el perfil normativo correspondiente y sepa qué canecas y qué reglas debe aplicar.",
     ["El usuario abre la aplicación por primera vez.",
      "El sistema muestra la lista de países disponibles en el catálogo local.",
      "El usuario selecciona su país.",
      "El sistema carga el perfil normativo correspondiente desde los recursos locales.",
      "El sistema valida el perfil contra su esquema y lo fija como perfil activo.",
      "El sistema muestra las canecas definidas por el perfil y continúa al escaneo de canecas."],
     ["El usuario no selecciona ningún país → el sistema aplica el perfil de Colombia como predeterminado y lo indica en pantalla.",
      "El perfil está corrupto o no valida contra el esquema → el sistema informa el error, conserva el perfil anterior y no interrumpe el uso de la aplicación.",
      "El usuario cambia de país desde ajustes → el sistema recarga el perfil y reinicia el conjunto de canecas disponibles."]),

    ("CUS-002", "Escanear y registrar las canecas disponibles", "Usuario",
     "El usuario apunta la cámara al conjunto de canecas de su entorno para que la aplicación registre cuáles existen y limite sus recomendaciones a lo que realmente está disponible.",
     ["El sistema abre la vista de escaneo de canecas.",
      "El usuario apunta la cámara hacia las canecas del lugar.",
      "El sistema detecta canecas por color y forma dentro del encuadre.",
      "El sistema empareja cada detección con las canecas definidas en el perfil activo.",
      "El sistema presenta el conjunto reconocido para su confirmación.",
      "El usuario confirma, añade o elimina canecas manualmente.",
      "El sistema guarda el conjunto de canecas disponibles y lo aplica a las clasificaciones siguientes."],
     ["No se reconoce ninguna caneca → el sistema ofrece la selección manual a partir de las canecas del perfil.",
      "Se detecta un color que no existe en el perfil activo → el sistema lo descarta e informa que esa caneca no pertenece al estándar del país seleccionado.",
      "El usuario omite el escaneo → el sistema asume disponibles todas las canecas declaradas por el perfil."]),

    ("CUS-003", "Clasificar un residuo mediante la cámara", "Usuario",
     "El usuario apunta la cámara a un residuo y la aplicación determina, sin conexión a internet, el material del residuo y la caneca destino según el perfil activo y las canecas disponibles.",
     ["El usuario abre la vista de clasificación y apunta la cámara al residuo.",
      "El sistema evalúa la calidad del encuadre.",
      "El sistema detecta y recorta el objeto principal del encuadre.",
      "El sistema clasifica el material del residuo con el modelo local correspondiente a la gama del dispositivo.",
      "El sistema consulta el motor de reglas con el material, el estado de contaminación, las canecas disponibles y el perfil activo.",
      "El sistema determina la caneca destino.",
      "El sistema muestra el nombre y el color de la caneca junto con la categoría del residuo.",
      "El sistema registra el resultado en el historial local sin almacenar la imagen."],
     ["La calidad de la imagen es insuficiente → se activa el caso de uso CUS-004 y no se emite resultado hasta corregir la toma.",
      "El material identificado requiere inspección interior → se activa el caso de uso CUS-005 antes de emitir el resultado.",
      "La confianza de la predicción está por debajo del umbral → se activa el caso de uso CUS-006.",
      "La caneca ideal no está entre las disponibles → el sistema propone la disponible más conservadora e informa el motivo."]),

    ("CUS-004", "Asistir la captura ante condiciones adversas", "Sistema",
     "El sistema evalúa de forma permanente la calidad de la imagen y guía al usuario para corregir iluminación, enfoque, encuadre o suciedad del lente, sin saturarlo de mensajes.",
     ["El sistema calcula la nitidez, la luminancia y el encuadre de cada fotograma analizado.",
      "El sistema compara los valores obtenidos con los umbrales aceptables.",
      "El sistema identifica la causa dominante de la degradación de la imagen.",
      "El sistema comprueba que ha transcurrido el intervalo mínimo desde la última indicación emitida.",
      "El sistema muestra una única indicación breve y contextual.",
      "El sistema retira la indicación en cuanto la condición se corrige."],
     ["Se detecta una mancha persistente en la misma posición entre fotogramas → el sistema sugiere limpiar el lente de la cámara.",
      "La condición adversa persiste tras varias indicaciones → el sistema propone la captura manual dirigida.",
      "Todas las métricas son aceptables → el sistema no muestra ninguna indicación y deja la vista de cámara despejada."]),

    ("CUS-005", "Detectar contaminación en residuos aprovechables", "Sistema",
     "Cuando el material identificado puede estar contaminado por su uso habitual, el sistema solicita una toma dirigida al interior o a la superficie crítica del residuo y lo reclasifica si detecta contaminación.",
     ["El sistema comprueba si el material identificado tiene una regla de inspección en el perfil activo.",
      "El sistema solicita al usuario la toma dirigida correspondiente.",
      "El usuario reorienta la cámara siguiendo la indicación.",
      "El sistema ejecuta el clasificador de contaminación sobre la nueva toma.",
      "El sistema obtiene el estado de contaminación con su nivel de confianza.",
      "El sistema vuelve a consultar el motor de reglas incorporando ese estado.",
      "El sistema emite la caneca definitiva indicando si la decisión se degradó por contaminación."],
     ["El usuario no proporciona la toma dirigida → el sistema aplica la ruta conservadora declarada en el perfil e informa que no pudo verificar el interior.",
      "La gama del dispositivo no permite ejecutar la etapa de forma automática → el sistema la ejecuta únicamente en captura manual dirigida.",
      "El resultado de contaminación no es concluyente → se activa el caso de uso CUS-006."]),

    ("CUS-006", "Gestionar resultados de baja confianza", "Usuario",
     "Ante una predicción por debajo del umbral de confianza, el sistema evita adivinar: solicita una nueva toma y, si la duda persiste, ofrece una alternativa conservadora o la selección manual de la categoría.",
     ["El sistema compara la confianza de la predicción con el umbral configurado.",
      "El sistema determina que la confianza es insuficiente.",
      "El sistema informa al usuario y solicita una nueva toma.",
      "El sistema reintenta la clasificación sobre la nueva toma.",
      "Si la duda persiste, el sistema propone la ruta conservadora del perfil y explica el motivo.",
      "El sistema ofrece al usuario seleccionar manualmente la categoría del residuo.",
      "El usuario elige una categoría y el sistema resuelve la caneca a partir de esa selección."],
     ["La nueva toma supera el umbral de confianza → el flujo prosigue con normalidad en el caso de uso CUS-003.",
      "El usuario rechaza la selección manual → el sistema mantiene la sugerencia conservadora y lo indica en el detalle."]),

    ("CUS-007", "Consultar la justificación normativa del resultado", "Usuario",
     "El usuario consulta por qué la aplicación asignó esa caneca, con la regla concreta que se aplicó y la referencia a la norma del país activo.",
     ["El sistema muestra el resultado de la clasificación.",
      "El usuario abre el detalle de la decisión.",
      "El sistema muestra el material identificado y la regla del perfil que se aplicó.",
      "El sistema muestra la referencia normativa declarada en el perfil activo.",
      "El sistema indica si la decisión se degradó por contaminación o por ausencia de la caneca ideal.",
      "El sistema muestra el aviso de que la recomendación tiene carácter orientativo."],
     ["El resultado provino de una selección manual del usuario → el sistema lo indica explícitamente.",
      "El perfil activo no declara referencia normativa → el sistema muestra únicamente la regla aplicada."]),

    ("CUS-008", "Ajustar capacidades según la gama del dispositivo", "Sistema",
     "Al arrancar, el sistema determina la gama real del dispositivo y habilita las funciones correspondientes, garantizando en todos los casos la clasificación por cámara.",
     ["El sistema consulta la memoria total, el número de núcleos, el nivel de API y los delegados de aceleración disponibles.",
      "El sistema ejecuta un micro-benchmark de inferencias de calentamiento.",
      "El sistema combina las señales obtenidas y fija la gama en baja, media o alta.",
      "El sistema selecciona la variante de modelo correspondiente a esa gama.",
      "El sistema habilita las funciones previstas para la gama detectada.",
      "El sistema deja activa la clasificación por cámara en cualquier caso."],
     ["La latencia observada se degrada de forma sostenida durante el uso → el sistema recalcula la gama a la baja.",
      "El usuario fija manualmente el nivel de rendimiento → el sistema respeta su elección y advierte del efecto sobre la latencia y el consumo.",
      "No hay ningún delegado de aceleración disponible → el sistema recurre al procesador sin interrumpir el servicio."]),

    ("CUS-009", "Consultar el historial local de clasificaciones", "Usuario",
     "El usuario revisa las clasificaciones anteriores almacenadas únicamente en el dispositivo y puede borrarlas cuando lo desee.",
     ["El usuario abre el historial de clasificaciones.",
      "El sistema recupera los registros almacenados localmente.",
      "El sistema muestra el material, la caneca y la fecha de cada registro.",
      "El usuario consulta el detalle de un registro concreto.",
      "El usuario solicita borrar el historial completo.",
      "El sistema pide confirmación, ejecuta el borrado y deja el historial vacío."],
     ["No existen registros → el sistema muestra un estado vacío explicativo.",
      "El usuario cancela el borrado → el sistema conserva los registros sin cambios."]),

    ("CUS-010", "Iniciar sesión — módulo preparado para versión futura", "Usuario",
     "La aplicación incorpora la pantalla y la infraestructura de inicio de sesión, operando en modo invitado mientras no exista un servicio de autenticación real.",
     ["El sistema muestra la pantalla de inicio de sesión con la opción de continuar como invitado.",
      "El usuario elige continuar como invitado.",
      "El sistema solicita la sesión al proveedor de autenticación configurado.",
      "El proveedor devuelve una sesión de invitado.",
      "El sistema concede acceso completo a las funciones de la versión actual."],
     ["El usuario intenta autenticarse con credenciales → el sistema informa que la función estará disponible en una versión futura y mantiene el modo invitado.",
      "Se conecta un proveedor de autenticación real en una versión posterior → la aplicación lo utiliza sin cambios en las capas superiores."]),
]

# (id, nombre, descripcion, actor, reglas, interoperabilidad, relaciones, [cus])
RF = [
    ("RF-001", "Selección de país en el primer arranque",
     "El usuario puede seleccionar el país en el que se encuentra durante el primer arranque de la aplicación.",
     "Usuario", "Solo se ofrecen países que tengan un perfil normativo disponible en el catálogo local.",
     "Catálogo de perfiles normativos del componente compartido.",
     "RF-002 (Carga del perfil normativo), RF-003 (Cambio de país desde ajustes)", ["CUS-001"]),

    ("RF-002", "Carga del perfil normativo",
     "El sistema debe cargar y validar el perfil normativo del país seleccionado desde los recursos locales del dispositivo.",
     "Sistema", "Un perfil que no valide contra el esquema se rechaza sin interrumpir el uso de la aplicación.",
     "Catálogo de perfiles en formato JSON; motor de reglas.",
     "RF-001 (Selección de país), RF-004 (Extensión del catálogo), RF-012 (Determinación de la caneca destino)", ["CUS-001", "CUS-003"]),

    ("RF-003", "Cambio de país desde ajustes",
     "El usuario puede cambiar el país activo en cualquier momento desde la pantalla de ajustes.",
     "Usuario", "Al cambiar de país se reinicia el conjunto de canecas disponibles registrado previamente.",
     "Pantalla de ajustes; repositorio de preferencias.",
     "RF-001 (Selección de país), RF-002 (Carga del perfil normativo), RF-007 (Confirmación de canecas)", ["CUS-001"]),

    ("RF-004", "Extensión del catálogo de perfiles",
     "El sistema debe permitir incorporar un país nuevo mediante la adición de un archivo de perfil normativo, sin modificar el código de la aplicación ni reentrenar los modelos.",
     "Sistema", "Ningún comportamiento específico de un país puede estar codificado en la aplicación.",
     "Recursos de perfiles; esquema de validación.",
     "RF-002 (Carga del perfil normativo)", ["CUS-001"]),

    ("RF-005", "Escaneo de canecas por cámara",
     "El usuario puede apuntar la cámara al conjunto de canecas de su entorno para que el sistema reconozca cuáles están disponibles.",
     "Usuario", "El escaneo es opcional; si se omite, se asumen disponibles todas las canecas del perfil activo.",
     "Módulo de cámara; detector de canecas.",
     "RF-006 (Reconocimiento y emparejamiento de canecas), RF-007 (Confirmación y edición manual)", ["CUS-002"]),

    ("RF-006", "Reconocimiento y emparejamiento de canecas",
     "El sistema debe reconocer las canecas presentes en el encuadre por color y forma, y emparejarlas con las definidas en el perfil activo.",
     "Sistema", "Un color que no exista en el perfil activo se descarta e informa al usuario.",
     "Detector de canecas; catálogo de perfiles.",
     "RF-005 (Escaneo de canecas), RF-002 (Carga del perfil normativo)", ["CUS-002"]),

    ("RF-007", "Confirmación y edición manual de canecas",
     "El usuario puede confirmar, añadir o eliminar manualmente las canecas disponibles antes de que el sistema las registre.",
     "Usuario", "El reconocimiento automático propone, pero la decisión final es siempre del usuario.",
     "Repositorio de disponibilidad de canecas.",
     "RF-006 (Reconocimiento de canecas), RF-008 (Restricción del resultado a canecas disponibles)", ["CUS-002"]),

    ("RF-008", "Restricción del resultado a las canecas disponibles",
     "El sistema debe limitar la caneca recomendada al conjunto de canecas disponibles y, cuando falte la caneca ideal, proponer la disponible más conservadora informando el motivo.",
     "Sistema", "Ante la ausencia de la caneca ideal se prefiere siempre la ruta de menor riesgo de contaminación del material aprovechable.",
     "Motor de reglas; repositorio de disponibilidad de canecas.",
     "RF-007 (Confirmación de canecas), RF-012 (Determinación de la caneca destino)", ["CUS-002", "CUS-003"]),

    ("RF-009", "Vista de cámara en vivo",
     "El usuario puede ver la imagen de la cámara en vivo mientras el sistema analiza el residuo.",
     "Usuario", "La vista en vivo no se bloquea en ningún momento mientras se ejecuta el análisis.",
     "Módulo de cámara; capa de presentación.",
     "RF-010 (Detección y recorte del objeto), RF-013 (Presentación del resultado), RF-017 (Emisión de indicaciones)", ["CUS-003"]),

    ("RF-010", "Detección y recorte del objeto",
     "El sistema debe detectar el objeto principal del encuadre y recortarlo antes de clasificarlo.",
     "Sistema", "En dispositivos de gama baja el recorte automático se sustituye por un marco guía fijo en pantalla.",
     "Módulo de inferencia; política de gama del dispositivo.",
     "RF-009 (Vista de cámara en vivo), RF-011 (Clasificación del material), RF-030 (Activación escalonada de funciones)", ["CUS-003"]),

    ("RF-011", "Clasificación del material del residuo",
     "El sistema debe clasificar el material del residuo mediante un modelo de red neuronal ejecutado íntegramente en el dispositivo.",
     "Sistema", "El modelo devuelve siempre un material con su confianza, nunca una caneca.",
     "Motor de ejecución LiteRT; modelos empaquetados en la aplicación.",
     "RF-010 (Detección y recorte del objeto), RF-012 (Determinación de la caneca destino), RF-023 (Umbral de confianza)", ["CUS-003"]),

    ("RF-012", "Determinación de la caneca destino",
     "El sistema debe determinar la caneca destino aplicando el motor de reglas sobre el material identificado, el estado de contaminación, las canecas disponibles y el perfil activo.",
     "Sistema", "La conversión de material a caneca ocurre exclusivamente en el motor de reglas.",
     "Motor de reglas; catálogo de perfiles normativos.",
     "RF-002 (Carga del perfil normativo), RF-008 (Restricción a canecas disponibles), RF-011 (Clasificación del material), RF-022 (Reclasificación por contaminación)", ["CUS-003"]),

    ("RF-013", "Presentación del resultado",
     "El sistema debe mostrar la caneca destino con su nombre y su color, junto con la categoría del residuo identificado.",
     "Sistema", "La caneca se comunica además mediante texto e icono, nunca únicamente mediante color.",
     "Capa de presentación.",
     "RF-012 (Determinación de la caneca destino), RF-026 (Explicación de la decisión)", ["CUS-003", "CUS-007"]),

    ("RF-014", "Operación sin conexión a internet",
     "El sistema debe realizar la clasificación completa sin ninguna conexión a internet ni llamada a servicios externos.",
     "Sistema", "Ninguna ruta de clasificación puede depender de la red.",
     "Módulo de inferencia; motor de reglas.",
     "RF-011 (Clasificación del material), RF-012 (Determinación de la caneca destino)", ["CUS-003"]),

    ("RF-015", "Evaluación de la calidad de imagen",
     "El sistema debe evaluar de forma permanente la nitidez, la iluminación y el encuadre de la imagen capturada.",
     "Sistema", "La evaluación se realiza con heurísticas y no con modelos adicionales, para no consumir el presupuesto de latencia de la clasificación.",
     "Módulo de cámara.",
     "RF-016 (Detección de suciedad en el lente), RF-017 (Emisión de indicaciones)", ["CUS-004"]),

    ("RF-016", "Detección de suciedad en el lente",
     "El sistema debe detectar manchas persistentes en la misma posición entre fotogramas e interpretarlas como suciedad del lente.",
     "Sistema", "Una mancha fija no debe confundirse con un objeto estático presente en el encuadre.",
     "Módulo de cámara.",
     "RF-015 (Evaluación de la calidad de imagen), RF-017 (Emisión de indicaciones)", ["CUS-004"]),

    ("RF-017", "Emisión de indicaciones de captura",
     "El sistema debe emitir indicaciones breves y contextuales que orienten al usuario para corregir la causa dominante de degradación de la imagen.",
     "Sistema", "Se emite una sola indicación a la vez, correspondiente a la causa dominante.",
     "Módulo de cámara; capa de presentación.",
     "RF-015 (Evaluación de la calidad de imagen), RF-016 (Detección de suciedad), RF-018 (Control de frecuencia)", ["CUS-003", "CUS-004"]),

    ("RF-018", "Control de frecuencia de las indicaciones",
     "El sistema debe respetar un intervalo mínimo entre indicaciones consecutivas y retirarlas en cuanto la condición adversa se corrija.",
     "Sistema", "Las indicaciones no pueden ser permanentes ni repetirse de forma continua.",
     "Módulo de cámara; capa de presentación.",
     "RF-017 (Emisión de indicaciones de captura)", ["CUS-004"]),

    ("RF-019", "Identificación de residuos que requieren inspección",
     "El sistema debe identificar, a partir de las reglas de inspección del perfil activo, qué materiales requieren una toma dirigida antes de emitir una decisión.",
     "Sistema", "Las reglas de inspección son datos del perfil normativo y no lógica de la aplicación.",
     "Motor de reglas; catálogo de perfiles.",
     "RF-002 (Carga del perfil normativo), RF-020 (Solicitud de toma dirigida), RF-021 (Clasificación de contaminación)", ["CUS-005"]),

    ("RF-020", "Solicitud de toma dirigida",
     "El sistema debe solicitar al usuario una toma dirigida al interior o a la superficie crítica del residuo cuando la regla de inspección lo requiera.",
     "Sistema", "El texto de la solicitud proviene del perfil normativo y pasa por el sistema de internacionalización.",
     "Capa de presentación; módulo de cámara.",
     "RF-019 (Identificación de residuos que requieren inspección), RF-021 (Clasificación de contaminación)", ["CUS-004", "CUS-005"]),

    ("RF-021", "Clasificación de contaminación",
     "El sistema debe determinar si el residuo aprovechable está limpio o contaminado mediante un segundo modelo ejecutado en el dispositivo.",
     "Sistema", "En dispositivos de gama baja esta etapa se ejecuta únicamente en captura manual dirigida.",
     "Motor de ejecución LiteRT; política de gama del dispositivo.",
     "RF-019 (Identificación de residuos que requieren inspección), RF-022 (Reclasificación por contaminación), RF-030 (Activación escalonada de funciones)", ["CUS-005"]),

    ("RF-022", "Reclasificación por contaminación",
     "El sistema debe reasignar el residuo a la caneca declarada como alternativa de contaminación en el perfil cuando detecte que está contaminado.",
     "Sistema", "La caneca alternativa por contaminación es un dato declarado en la regla de cada material dentro del perfil.",
     "Motor de reglas.",
     "RF-012 (Determinación de la caneca destino), RF-021 (Clasificación de contaminación), RF-026 (Explicación de la decisión)", ["CUS-005"]),

    ("RF-023", "Umbral de confianza",
     "El sistema debe comparar la confianza de cada predicción con un umbral configurado y abstenerse de emitir un resultado cuando no lo alcance.",
     "Sistema", "Ante la duda el sistema no adivina un resultado.",
     "Módulo de inferencia; motor de reglas.",
     "RF-011 (Clasificación del material), RF-024 (Sugerencia conservadora), RF-025 (Selección manual de categoría)", ["CUS-006"]),

    ("RF-024", "Sugerencia conservadora ante duda persistente",
     "El sistema debe proponer la ruta conservadora declarada en el perfil cuando la duda persista tras reintentar la toma, explicando el motivo al usuario.",
     "Sistema", "La ruta conservadora se declara en el perfil normativo de cada país.",
     "Motor de reglas; capa de presentación.",
     "RF-023 (Umbral de confianza), RF-026 (Explicación de la decisión)", ["CUS-006"]),

    ("RF-025", "Selección manual de categoría",
     "El usuario puede seleccionar manualmente la categoría del residuo cuando el sistema no logre identificarlo con confianza suficiente.",
     "Usuario", "El resultado obtenido por selección manual se marca como tal en el detalle y en el historial.",
     "Capa de presentación; motor de reglas.",
     "RF-023 (Umbral de confianza), RF-024 (Sugerencia conservadora), RF-032 (Registro local de clasificaciones)", ["CUS-006"]),

    ("RF-026", "Explicación de la decisión",
     "El sistema debe mostrar la regla del perfil normativo que se aplicó para llegar a la caneca recomendada.",
     "Sistema", "Toda decisión debe ser explicable a partir de una regla del perfil activo.",
     "Motor de reglas; capa de presentación.",
     "RF-012 (Determinación de la caneca destino), RF-013 (Presentación del resultado), RF-027 (Referencia normativa)", ["CUS-007"]),

    ("RF-027", "Referencia normativa",
     "El sistema debe mostrar la referencia a la norma vigente declarada en el perfil del país activo.",
     "Sistema", "Si el perfil no declara referencia normativa, se muestra únicamente la regla aplicada.",
     "Catálogo de perfiles; capa de presentación.",
     "RF-002 (Carga del perfil normativo), RF-026 (Explicación de la decisión)", ["CUS-007"]),

    ("RF-028", "Aviso de carácter orientativo",
     "El sistema debe mostrar de forma visible que la recomendación tiene carácter orientativo y no sustituye la normativa local aplicable.",
     "Sistema", "El aviso debe estar presente en la pantalla de resultado de la clasificación.",
     "Capa de presentación.",
     "RF-026 (Explicación de la decisión), RF-027 (Referencia normativa)", ["CUS-007"]),

    ("RF-029", "Detección de la gama del dispositivo",
     "El sistema debe determinar la gama del dispositivo combinando sus capacidades declaradas con un micro-benchmark de inferencia ejecutado al arrancar.",
     "Sistema", "La latencia medida en el dispositivo prevalece sobre las capacidades declaradas por el sistema operativo.",
     "Política de gama; módulo de inferencia.",
     "RF-030 (Activación escalonada de funciones), RF-031 (Ajuste manual del nivel de rendimiento)", ["CUS-008"]),

    ("RF-030", "Activación escalonada de funciones",
     "El sistema debe habilitar las funciones auxiliares según la gama detectada, manteniendo operativa la clasificación por cámara en todas las gamas.",
     "Sistema", "La clasificación por cámara no se deshabilita en ningún caso, cualquiera que sea la gama del dispositivo.",
     "Política de gama; módulos de cámara e inferencia.",
     "RF-010 (Detección y recorte del objeto), RF-021 (Clasificación de contaminación), RF-029 (Detección de la gama)", ["CUS-008"]),

    ("RF-031", "Ajuste manual del nivel de rendimiento",
     "El usuario puede fijar manualmente el nivel de rendimiento desde ajustes, sobrescribiendo la gama detectada automáticamente.",
     "Usuario", "El sistema advierte del efecto del ajuste manual sobre la latencia y el consumo del dispositivo.",
     "Pantalla de ajustes; política de gama.",
     "RF-029 (Detección de la gama), RF-030 (Activación escalonada de funciones)", ["CUS-008"]),

    ("RF-032", "Registro local de clasificaciones",
     "El sistema debe registrar en el dispositivo el resultado de cada clasificación, sin almacenar en ningún caso la imagen capturada.",
     "Sistema", "Las imágenes de la cámara nunca se persisten ni se registran en trazas.",
     "Repositorio de historial; base de datos local.",
     "RF-012 (Determinación de la caneca destino), RF-033 (Consulta del historial)", ["CUS-003", "CUS-009"]),

    ("RF-033", "Consulta del historial",
     "El usuario puede consultar el historial de clasificaciones con el material, la caneca y la fecha de cada registro.",
     "Usuario", "El historial es estrictamente local al dispositivo y no se sincroniza.",
     "Repositorio de historial; capa de presentación.",
     "RF-032 (Registro local de clasificaciones), RF-034 (Borrado del historial)", ["CUS-009"]),

    ("RF-034", "Borrado del historial",
     "El usuario puede borrar el historial completo de clasificaciones desde la propia aplicación.",
     "Usuario", "El borrado requiere confirmación explícita del usuario.",
     "Repositorio de historial.",
     "RF-032 (Registro local de clasificaciones), RF-033 (Consulta del historial)", ["CUS-009"]),

    ("RF-035", "Pantalla de inicio de sesión preparada",
     "El sistema debe incorporar la pantalla de inicio de sesión con la opción de continuar como invitado.",
     "Usuario", "En la versión actual la autenticación con credenciales no está operativa.",
     "Capa de presentación; proveedor de autenticación.",
     "RF-036 (Abstracción del proveedor de autenticación), RF-037 (Modo invitado por defecto)", ["CUS-010"]),

    ("RF-036", "Abstracción del proveedor de autenticación",
     "El sistema debe acceder a la sesión mediante una interfaz de proveedor de autenticación, independiente de la implementación concreta que se use.",
     "Sistema", "Ninguna capa superior puede depender de un proveedor de autenticación concreto.",
     "Puerto de autenticación del componente compartido.",
     "RF-035 (Pantalla de inicio de sesión), RF-037 (Modo invitado por defecto)", ["CUS-010"]),

    ("RF-037", "Modo invitado por defecto",
     "El sistema debe operar en modo invitado de forma predeterminada, con acceso completo a las funciones de la versión actual.",
     "Sistema", "Ninguna función de la versión actual puede exigir una cuenta de usuario.",
     "Proveedor de autenticación.",
     "RF-035 (Pantalla de inicio de sesión), RF-036 (Abstracción del proveedor)", ["CUS-010"]),
]

# (id, nombre, descripcion, prioridad, [cus])
RNF = [
    ("RNF-001", "Tiempo de respuesta de la clasificación",
     "El tiempo transcurrido entre apuntar la cámara al residuo y obtener la caneca destino debe ser inferior a 2 segundos en dispositivos de gama media y no superar los 4 segundos en gama baja. Se trata de un objetivo de diseño y no de una restricción bloqueante: si un dispositivo concreto no lo alcanza, la aplicación degrada funciones auxiliares pero mantiene operativa la clasificación por cámara.",
     "Alta", ["CUS-003", "CUS-008"]),

    ("RNF-002", "Operación totalmente sin conexión",
     "La aplicación debe funcionar de forma completa en modo avión. Ninguna función de clasificación puede depender de servicios en la nube, interfaces de programación externas ni modelos alojados remotamente.",
     "Alta", ["CUS-001", "CUS-002", "CUS-003"]),

    ("RNF-003", "Compatibilidad de dispositivos",
     "La aplicación debe funcionar en Android 8.0, nivel de API 26, o superior, en arquitecturas ARM de 32 y 64 bits, cubriendo desde dispositivos de gama baja hasta gama alta.",
     "Alta", ["CUS-003", "CUS-008"]),

    ("RNF-004", "Escalabilidad del catálogo normativo",
     "Incorporar un país nuevo al sistema debe requerir únicamente añadir un archivo de perfil normativo, sin modificar el código de la aplicación ni reentrenar los modelos de clasificación.",
     "Alta", ["CUS-001"]),

    ("RNF-005", "Portabilidad a iOS",
     "El componente compartido debe compilar para iOS sin modificaciones y no debe contener ninguna dependencia de plataforma, de modo que la fase de desarrollo para iOS consista en implementar adaptadores y no en reescribir lógica.",
     "Alta", ["CUS-010"]),

    ("RNF-006", "Tamaño de la aplicación",
     "El paquete instalado, incluidos los modelos de clasificación empaquetados, no debe superar los 150 MB.",
     "Media", ["CUS-003", "CUS-008"]),

    ("RNF-007", "Consumo de memoria y energía",
     "El uso máximo de memoria durante la clasificación continua no debe superar los 350 MB, y una sesión de uso de 5 minutos no debe provocar limitación térmica en dispositivos de gama media.",
     "Media", ["CUS-008"]),

    ("RNF-008", "Exactitud de la clasificación",
     "El sistema debe alcanzar al menos un 85 % de exactitud top-1 en la identificación del material y al menos un 95 % de acierto en la ruta de disposición, medidos sobre un conjunto de datos no utilizado durante el entrenamiento.",
     "Alta", ["CUS-003", "CUS-005", "CUS-006"]),

    ("RNF-009", "Usabilidad y lenguaje visual",
     "La interfaz debe seguir un lenguaje visual minimalista de inspiración iOS, permitir llegar al primer resultado en un máximo de tres toques desde la apertura de la aplicación, y no presentar elementos que interrumpan de forma persistente la vista de cámara.",
     "Alta", ["CUS-004", "CUS-006", "CUS-007"]),

    ("RNF-010", "Accesibilidad",
     "La interfaz debe cumplir el nivel de contraste AA, admitir tamaños de fuente dinámicos y ser navegable con lector de pantalla. Ninguna información esencial puede transmitirse únicamente mediante color.",
     "Media", ["CUS-007"]),

    ("RNF-011", "Internacionalización",
     "Todo texto visible debe estar externalizado en recursos de cadenas. El idioma predeterminado es el español, con la estructura preparada para incorporar idiomas adicionales sin cambios en el código.",
     "Media", ["CUS-001", "CUS-007"]),

    ("RNF-012", "Privacidad de los datos",
     "Las imágenes capturadas no deben salir del proceso de la aplicación: no se persisten en almacenamiento, no se transmiten por red y no se registran en trazas. El historial almacena únicamente el resultado de la clasificación.",
     "Alta", ["CUS-003", "CUS-009"]),

    ("RNF-013", "Tolerancia a fallos",
     "Ante la indisponibilidad de la cámara, de un delegado de aceleración o de un modelo, la aplicación debe degradar sus funciones de forma controlada e informar al usuario, sin cerrarse de manera inesperada.",
     "Alta", ["CUS-002", "CUS-004", "CUS-005", "CUS-006", "CUS-008"]),

    ("RNF-014", "Persistencia local",
     "La configuración de país, el conjunto de canecas disponibles y el historial de clasificaciones deben sobrevivir al cierre y la reapertura de la aplicación.",
     "Media", ["CUS-001", "CUS-002", "CUS-009"]),

    ("RNF-015", "Mantenibilidad y trazabilidad",
     "El código debe organizarse en capas con dependencias dirigidas hacia el dominio, cada módulo debe ser trazable a los requerimientos que implementa, y la lógica de dominio debe alcanzar al menos un 70 % de cobertura de pruebas automatizadas.",
     "Media", ["CUS-003", "CUS-010"]),

    ("RNF-016", "Reproducibilidad del entrenamiento",
     "El proceso de preparación de datos y entrenamiento debe ser reproducible desde cero con semillas fijas y particiones versionadas. Todo resultado de exactitud debe reportarse sobre un conjunto de datos no visto durante el entrenamiento.",
     "Alta", ["CUS-003", "CUS-005"]),

    ("RNF-017", "Licenciamiento de las fuentes de datos",
     "Todo conjunto de datos incorporado al entrenamiento debe tener su licencia documentada y verificada como compatible con el uso previsto del proyecto.",
     "Media", ["CUS-003", "CUS-005"]),
]

OBSERVACIONES = (
    "El proyecto se desarrolla mediante agentes de inteligencia artificial trabajando de forma concurrente sobre "
    "ámbitos de archivos disjuntos, bajo la dirección, revisión e integración de Juan Urrego. El plan de trabajo "
    "está estructurado en diez hitos, uno por agente, con un único tramo secuencial inicial dedicado a fijar la "
    "estructura de módulos y los contratos de interfaces entre agentes. A partir de ese punto cada agente trabaja "
    "contra implementaciones simuladas deterministas de los módulos vecinos, lo que elimina las esperas entre "
    "flujos de trabajo.\n\n"
    "La entrega se organiza en fases. La versión 1,0 comprende únicamente la aplicación Android como demostración "
    "funcional, con las dos funciones esenciales del producto: la clasificación de residuos por cámara y el escaneo "
    "de las canecas disponibles. La fase 2 corresponde a la aplicación iOS, reutilizando el componente compartido. "
    "La fase 3 incorpora el inicio de sesión y la base de datos reales, cuya infraestructura queda preparada en esta "
    "versión mediante un puerto de autenticación y una implementación en modo invitado. La instalación sobre cámaras "
    "fijas en puntos de disposición se documenta como fase 4 y queda expresamente fuera del alcance de este "
    "documento.\n\n"
    "Existe una restricción dura que condiciona el diseño del componente de aprendizaje automático: el proyecto no "
    "dispone de capacidad para recolectar ni etiquetar un conjunto de datos propio. En consecuencia, los modelos se "
    "entrenan exclusivamente sobre conjuntos de datos públicos unificados mediante un mapeo de taxonomía versionado, "
    "ampliados con augmentación orientada al dominio móvil y complementados con contaminación sintética generada por "
    "segmentación y composición, dado que no existe ningún conjunto público de reciclables contaminados. Esta "
    "restricción se refleja en los requerimientos RNF-008, RNF-016 y RNF-017, y constituye el principal riesgo "
    "técnico del proyecto."
)

APROBACIONES = [
    ("Gestor de Proyectos", "Juan Urrego", FECHA, ""),
    ("Analista de requerimientos", "Juan Urrego", FECHA, ""),
    ("Responsable de revisión", "Juan Urrego", FECHA, ""),
]
