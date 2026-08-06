# BotaBien

Aplicación móvil que, usando la cámara y redes neuronales que corren **100% en el dispositivo**, te dice en qué caneca va cualquier residuo según la norma vigente de tu país.

## ¿De qué trata?

Separar residuos correctamente falla por dos razones: la gente no sabe a qué caneca corresponde cada material, y las reglas reales son más sutiles de lo que parecen. Un vaso de cartón de café aparenta ser papel reciclable, pero lleva recubrimiento de polietileno y suele tener residuo líquido dentro: en Colombia va a la caneca **negra**, no a la blanca. El código de colores de la Resolución 2184 de 2019 exige explícitamente que lo aprovechable esté "limpio y seco", y esa condición es invisible desde afuera del objeto.

BotaBien resuelve ambas cosas. Una primera red clasifica el material del residuo; una segunda etapa inspecciona si está contaminado y degrada la decisión cuando corresponde, pidiéndole activamente al usuario la toma que hace falta ("apunta hacia adentro del vaso"). El resultado no se calcula contra una tabla fija sino contra un **perfil normativo intercambiable**: el modelo predice materiales, y un motor de reglas independiente traduce material a caneca según el país activo. Agregar un país nuevo es agregar un archivo de perfil, no reentrenar la red.

Todo ocurre sin conexión a internet. No se envían imágenes a ningún servidor, no hay dependencia de APIs externas y la aplicación funciona desde gama baja hasta gama alta, habilitando funciones de forma escalonada según la capacidad real del dispositivo.

## Stack técnico

| Aspecto | Elección |
|---|---|
| Lenguaje | Kotlin 2.x (dominio y app Android), Python 3.11 (entrenamiento) |
| Arquitectura | Kotlin Multiplatform — módulo `:shared` sin dependencias de plataforma |
| UI Android | Jetpack Compose + Material 3 con design system propio de estética iOS |
| Cámara | CameraX (Android) · AVFoundation (iOS, fase 2) |
| Inferencia | LiteRT (ex TensorFlow Lite) con delegados NNAPI / GPU |
| Modelos | MobileNetV3 y EfficientNet-Lite cuantizados INT8 |
| Entrenamiento | PyTorch → ONNX → LiteRT, con Google Colab o Kaggle |
| Persistencia | SQLDelight (multiplataforma) + DataStore para preferencias |
| Inyección de dependencias | Koin |
| Serialización | kotlinx.serialization (perfiles normativos en JSON) |
| Idioma del código | Inglés en identificadores, español en documentación y UI |
| Estilo | Clean Architecture por capas, orientado a objetos, dominio puro |
| Plataformas objetivo | Android 8.0+ (API 26) en v1 · iOS en fase 2 |

### Por qué Kotlin Multiplatform y no Flutter ni Kotlin nativo puro

El requisito de portabilidad a iOS desde el día uno descarta Kotlin nativo puro: obligaría a reescribir el motor de reglas y toda la lógica de decisión en Swift, duplicando la fuente de verdad más crítica del producto. Flutter tampoco encaja: la inferencia on-device y el acceso a cámara terminarían pasando por *platform channels* de todos modos, añadiendo una capa de plugins de terceros justo en la ruta de latencia que más nos importa, y complicando el uso de los delegados de aceleración por hardware.

Kotlin Multiplatform permite compartir exactamente lo que debe compartirse — dominio, motor de reglas, perfiles normativos, política de *tiering*, persistencia — y mantener nativo lo que debe ser nativo: cámara, runtime de inferencia y UI. La interfaz se implementa por plataforma (Compose en Android, SwiftUI en iOS) porque el objetivo estético es que cada una se sienta natural, no que se vean iguales.

## Alcance

**MVP (v1, solo Android):**

- Selección de país y carga del perfil normativo correspondiente (Colombia, Resolución 2184 de 2019)
- Clasificación de residuos por cámara, en local, sin conexión
- Escaneo por cámara de las canecas disponibles en el entorno para restringir el resultado a lo que realmente existe
- Detección de contaminación con toma dirigida y reclasificación cuando aplica
- Asistencia dinámica de captura ante mala luz, desenfoque, lente sucio o mal encuadre
- Adaptación automática de funciones según la gama del dispositivo (bajo / medio / alto)
- Justificación normativa de cada decisión
- Historial local de clasificaciones
- Pantalla e infraestructura de inicio de sesión preparadas, sin backend activo

**Visión a futuro:**

- Fase 2: aplicación iOS reutilizando el módulo `:shared`
- Fase 3: inicio de sesión y base de datos reales, sincronización y estadísticas
- Fase 4: cámaras fijas montadas sobre canecas en instalaciones institucionales
- Ampliación del catálogo de países y de un asistente conversacional local para dudas

**Fuera de alcance en v1:**

- Cualquier llamada a servicios de IA en la nube o APIs externas en tiempo de clasificación
- Backend, cuentas de usuario reales y sincronización entre dispositivos
- Despliegue en cámaras fijas o hardware dedicado
- Recolección y etiquetado manual de un dataset propio: los modelos se entrenan exclusivamente sobre datasets públicos, augmentación y contaminación sintética

## Documentación

- [Análisis y especificación de requerimientos](docs/F_Analisis_de_Requerimientos_V1,0_BotaBien.docx)
- [Arquitectura y diagramas](docs/arquitectura.md)
- [Plan de trabajo](plan/plan_de_trabajo.md)
- [Contexto para vibe coding](context-for-vibe-coding.md)

## Cómo empezar

```bash
git clone https://github.com/<usuario>/BotaBien.git
cd BotaBien
./gradlew :androidApp:assembleDebug     # compilar la app Android
./gradlew :shared:allTests              # pruebas del dominio compartido
```

Requisitos previos: JDK 17, Android Studio Ladybug o superior, SDK de Android 34 y un dispositivo o emulador con API 26 o superior. El pipeline de entrenamiento vive en `ml/` y se ejecuta aparte con Python 3.11 y las dependencias de `ml/requirements.txt`.

## Equipo

| Rol | Persona asignada |
|---|---|
| Dirección del proyecto, análisis y revisión | Juan Urrego |
| Implementación | Agentes de IA en paralelo (ver `plan/plan_de_trabajo.md`) |

El desarrollo se ejecuta mediante agentes de IA trabajando concurrentemente sobre issues independientes. La partición de responsabilidades, los contratos entre módulos y las reglas para evitar colisiones están definidos en [`context-for-vibe-coding.md`](context-for-vibe-coding.md).
