# Design system — BotaBien

Sistema de diseño de la capa de interfaz Android (`androidApp/ui/`). Implementa RNF-009:
estética minimalista de inspiración iOS — limpia, con mucho aire, tipografía cuidada y
transiciones suaves. **Todo componente visual sale de aquí**: ningún archivo de la capa de
interfaz declara colores, estilos de texto, radios, espaciados ni curvas de animación propios.

## Principios

1. **Un solo acento.** El verde de marca es el único color protagonista; todo lo demás son
   neutros y tonos semánticos puntuales. Nada de superficies saturadas.
2. **Aire antes que ornamento.** La jerarquía se construye con tipografía y espaciado, no con
   bordes, sombras ni contenedores anidados. Ante la duda, más espacio.
3. **Sin ripple.** La respuesta táctil es la de iOS: el control se encoge (`0.96`) y atenúa
   su contenido con un muelle sin rebote, en lugar del ripple de Material.
4. **El color de caneca es dato, no diseño.** Los colores de caneca vienen del perfil
   normativo (`BinDefinition.colorHex`) y se convierten en color en tiempo de ejecución.
   El design system no los conoce; sí define las superficies neutras que los rodean.
5. **Nunca solo color.** Todo estado se comunica también con texto o icono (RNF-010), y todo
   texto visible viene de recursos de cadenas (RNF-011).

## Uso

El árbol de composición entero vive dentro de `BotaBienTheme`, que publica los tokens y
configura por debajo un `MaterialTheme` equivalente para los componentes internos de
Material 3. Las pantallas leen **siempre** de `BotaTheme`:

```kotlin
BotaBienTheme {          // en MainActivity; decide claro/oscuro con isSystemInDarkTheme()
    // ...
    Text(
        text = stringResource(R.string.result_title),
        style = BotaTheme.typography.title2,
        color = BotaTheme.colors.label,
    )
}
```

Prohibido en la capa de interfaz (se rechaza en revisión):

- `Color(0xFF...)`, `Color.Red`, etc. → usar `BotaTheme.colors`.
- `TextStyle(...)`, `fontSize = ...sp` → usar `BotaTheme.typography`.
- `RoundedCornerShape(...)` → usar `BotaTheme.shapes`.
- `MaterialTheme.colorScheme` / `MaterialTheme.typography` → leer `BotaTheme`.
- Duraciones o easings sueltos → usar `BotaMotion`.
- Valores de `dp` para separar contenido → usar `BotaTheme.spacing`.

## Tokens

### Color (`BotaTheme.colors`)

Paleta semántica calcada de los colores de sistema de iOS, con esquema claro y oscuro.
Los tonos semánticos del esquema claro usan las variantes accesibles (contraste AA, RNF-010).

| Token | Uso |
|---|---|
| `accent` / `onAccent` | Acción principal y contenido sobre ella |
| `background` | Fondo de pantallas planas |
| `groupedBackground` | Fondo de pantallas con tarjetas (estilo lista agrupada iOS) |
| `surfaceElevated` | Tarjetas y hojas |
| `label` / `secondaryLabel` / `tertiaryLabel` | Jerarquía de texto |
| `fill` / `secondaryFill` | Rellenos sutiles de controles neutros |
| `separator` | Divisores finos |
| `success` / `warning` / `error` / `info` | Estados semánticos |
| `scrim` / `onScrim` | Velo sobre la cámara y bajo las hojas; contenido sobre él |
| `cameraBackdrop` | Fondo del área de cámara, oscuro en ambos temas |
| `isDark` | Verdadero en el esquema oscuro |

### Tipografía (`BotaTheme.typography`)

Escala de iOS sobre la tipografía del sistema:

| Token | Tamaño/interlínea | Peso | Uso |
|---|---|---|---|
| `largeTitle` | 34/41 | Bold | Portada de pantalla |
| `title1` | 28/34 | Bold | Título de primer nivel |
| `title2` | 22/28 | Bold | Título de sección |
| `title3` | 20/25 | SemiBold | Subsección |
| `headline` | 17/22 | SemiBold | Botones, cabeceras de celda |
| `body` | 17/22 | Regular | Cuerpo por defecto |
| `callout` | 16/21 | Regular | Cuerpo compacto |
| `subheadline` | 15/20 | Regular | Texto secundario |
| `footnote` | 13/18 | Regular | Aclaraciones, metadatos |
| `footnoteEmphasized` | 13/18 | SemiBold | Píldoras de estado |
| `caption1` | 12/16 | Regular | Leyendas |
| `caption2` | 11/13 | Regular | Leyenda mínima |

### Espaciado (`BotaTheme.spacing`)

Escala en pasos de 4 dp: `xxs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 20 · xxl 24 · xxxl 32`,
más `screenMargin` (20 dp) como margen horizontal estándar de toda pantalla.

### Radios (`BotaTheme.shapes`)

`small 10 · medium 14 (botones) · large 20 (tarjetas) · sheet 28 superior (hojas) · capsule`.

### Movimiento (`BotaMotion`)

- Duraciones: `DURATION_FAST_MS 200 · DURATION_BASE_MS 300 · DURATION_SLOW_MS 450`.
- Curvas: `easeInOut` (la estándar de iOS) y `easeOut` para contenido entrante.
- Muelles: `pressSpring()` para respuesta táctil, `surfaceSpring()` para hojas y
  superposiciones.
- Constantes táctiles: `PRESSED_SCALE 0.96 · PRESSED_ALPHA 0.75`.
- Arranque: `DURATION_LAUNCH_SETTLE_MS 200 · DURATION_LAUNCH_GROWTH_MS 620 ·
  DURATION_LAUNCH_HOLD_MS 100 · DURATION_LAUNCH_EXIT_MS 260`, con la curva `growth`.
  Son exclusivas de la entrada de marca; ninguna pantalla las usa.

## Marca

### Logo

Un bote de basura lleno de tierra del que brota una flor: el residuo bien clasificado
se convierte en vida. El brote domina la composición —ocupa dos tercios del alto— para
que lo primero que se lea sea la flor y no la basura.

Es **monocromático por diseño**. La profundidad sale de la opacidad de la tierra (33 %) y
de la segunda hoja (55 %), nunca de un segundo tono: por eso la variante monocroma del
icono de lanzador es el mismo dibujo sin retocar nada.

El logo existe en tres formatos porque cada consumidor exige el suyo, y
`BotaLogoResourcesTest` **rompe el build si divergen**:

| Dónde | Para qué |
|---|---|
| `BotaLogoPaths` (en `BotaLogo.kt`) | **Origen de verdad** de las curvas |
| `drawable/ic_launcher_foreground.xml` · `ic_launcher_monochrome.xml` | Capas del icono adaptativo, en el lienzo de 108 dp |
| `docs/brand/botabien-logo.svg` | Maestro para usos externos: favicon, tienda, presentaciones |

No hay un `VectorDrawable` suelto del logo: no lo consumía nadie, y un recurso sin
consumidor es código muerto que además ensucia el Lint. Si algún día hace falta uno
—por ejemplo para `windowSplashScreenAnimatedIcon`— se añade **con** su consumidor y
su fila en esta tabla.

```kotlin
BotaLogo(
    modifier = Modifier.size(108.dp),
    color = BotaTheme.colors.accent,  // por defecto, el acento del tema
    growth = 1f,                      // 0 = solo el bote con su tierra; 1 = logo completo
)
```

`growth` existe para la entrada de marca: reparte un único progreso entre el tallo, las
dos hojas y la flor con tramos solapados, de modo que el gesto se lea continuo. El logo
estático se dibuja siempre con `1f`, que es el valor por defecto.

El icono de lanzador es vectorial en todas las densidades: con `minSdk 26` todos los
dispositivos entienden el icono adaptativo, así que no hay mapas de bits que mantener.

### Entrada de marca (`BotaLaunchScreen`)

`MainActivity` **envuelve** la raíz con ella en vez de precederla: el contenido se compone
desde el primer fotograma por debajo del velo, así que la animación tapa el arranque sin
alargarlo. Dura ~1,1 s en total y no vuelve a aparecer al rotar.

Respeta el ajuste de sistema de animaciones (`ANIMATOR_DURATION_SCALE == 0`, que es como
Android expone «reducir movimiento»): en ese caso el logo aparece quieto, se sostiene
220 ms y se retira. Se conserva la marca y se elimina el movimiento.

El fondo del velo es `BotaTheme.colors.background`, el mismo de la app, para que la
retirada no tenga salto de color. El tema de ventana (`Theme.BotaBien`, con su variante
`-night`) fija ese mismo color como `windowBackground` y elimina el destello blanco que
había al abrir la app en oscuro.

## Componentes

Todos en `com.botabien.android.ui.components`. Los textos llegan siempre por parámetro
desde un recurso de cadenas; ningún componente trae texto propio.

### `BotaButton`

```kotlin
BotaButton(
    text = stringResource(R.string.action_classify),
    onClick = { /* ... */ },
    modifier = Modifier.fillMaxWidth(), // la acción principal se extiende; inline, sin él
    style = BotaButtonStyle.Filled,   // Filled | Tinted | Plain, énfasis decreciente
    compact = false,                  // true → 44 dp para contextos densos
)
```

Regla de composición: **una sola acción `Filled` por pantalla**; el resto `Tinted` o `Plain`.
El ancho lo decide el llamador: `fillMaxWidth()` para la acción principal, contenido
natural para botones de barra o fila.

Acciones destructivas: `destructive = true` pasa el contenido al color de error, al estilo
de las acciones rojas de iOS. Solo existe en `Tinted` y `Plain` (la variante rellena
degradaría el contraste); una petición `Filled` destructiva se presenta como `Tinted`.

### `BotaSelectionMark`

Marca de verificación para listas de selección única (país, nivel de rendimiento).
Acompáñala siempre de `contentDescription` («Seleccionado») para lectores de pantalla.

### `BotaRouteGlyph`

Glifo geométrico por ruta de disposición (triángulo aprovechable, cuadrado no
aprovechable, círculo orgánico, rombo peligroso, estrella recolección especial). La
caneca se comunica por color, **texto e icono**, nunca solo por color (RNF-010). Se
dibuja en el color del contenido circundante y siempre acompaña a un texto, así que es
decorativo para lectores de pantalla.

### Nota de contraste (RNF-010)

`secondaryLabel` claro usa 76 % de opacidad (frente al 60 % de iOS) para cumplir AA
(≥ 4,5:1) como texto normal sobre fondos claros. Los tonos semánticos del esquema claro
son las variantes accesibles de iOS y superan AA sobre blanco; el esquema oscuro cumple
con los valores originales.

### `BotaCard`

```kotlin
BotaCard(onClick = { /* opcional: tarjeta pulsable */ }) {
    // contenido en columna; relleno lg por defecto
}
```

Superficie plana sin sombra. Se usa sobre `groupedBackground` para que el contraste de
fondos —no un borde— delimite la tarjeta.

### `BotaGlass` — el material de cristal

Superficie translúcida para lo que **flota sobre contenido**: el cromo de la cámara, el
marco de encuadre, la tarjeta de decisión. Es el lenguaje de iOS 26: atenuación que
sostiene la legibilidad, lavado de luz cenital y brillo especular en el borde.

```kotlin
BotaGlass(
    shape = BotaTheme.shapes.large,
    tint = binColor(disposal.bin.colorHex), // opcional: el color de caneca, como lavado
    state = BotaGlassState.Settled,         // Settled | Analyzing | Uncertain
) { /* contenido */ }

Modifier.botaGlass(shape = BotaTheme.shapes.capsule)  // para lo que ya tiene disposición
```

**Reglas**

1. **Cristal solo en la capa que flota.** Nunca en la capa de contenido, y nunca cristal
   sobre cristal. Si algo no tiene nada detrás que dejar pasar, es una `BotaCard`.
2. **La atenuación no se negocia.** Va siempre, incluso en el material más transparente:
   es lo único que sostiene el contraste del texto cuando detrás hay vídeo en vivo
   (RNF-010).
3. **El tinte es dato.** El color de caneca sale del perfil (`BinDefinition.colorHex`) y
   atraviesa el cristal como lavado al 16 %, nunca como color de fondo. El design system
   sigue sin conocer ningún color de caneca.
4. **El grado no lo elige el llamador**, lo resuelve el tema.

**Las cuatro capas.** Las cuatro hacen falta; quitar una lo devuelve a parecer un velo gris:

1. **Atenuación** — sostiene el contraste del texto sobre vídeo en vivo.
2. **Luz cenital** — el material parece iluminado desde arriba.
3. **Canto biselado** — bandas de luz pegadas al borde superior e inferior. Es lo que da
   sensación de *grosor*; sin ellas la superficie parece pintada sobre la pantalla.
4. **Brillo especular** en el contorno, más el sombreado exterior que la despega del fondo.

La primera versión llevaba solo 1, 2 y un contorno de 1 px, y **en el dispositivo no se
distinguía del velo plano anterior**. El bisel y la sombra son lo que marcó la diferencia.

**Grados** (`BotaTheme.glass`, resuelto una vez en `BotaBienTheme`)

| Grado | Cuándo | Qué hace |
|---|---|---|
| `Clear` | Por defecto | Translúcido, atenuado y tintado, con canto y brillo en el borde |
| `Veil` | Animaciones desactivadas por el usuario | Superficie opaca y borde tenue |

La jerarquía y la legibilidad **son idénticas en los dos grados**; solo cambia el material.

**Solo se degrada por preferencia, no por potencia**, y es un cambio deliberado: sin
desenfoque de fondo el cristal son dos degradados y un borde, o sea lo que cuesta
cualquier superficie plana. La versión inicial degradaba también con poca memoria o con
el ahorro de energía activo, lo que no ahorraba nada y dejaba sin material a cualquiera
con el ahorro de batería encendido. Si algún día se añade desenfoque real —eso sí
cuesta— la degradación por gama vuelve a tener sentido: consúltese `DeviceTierPolicy`
por caso de uso y degrádese el subárbol con `LocalGlassMaterial`.

**Sobre qué fondo se ve.** El material necesita algo detrás que dejar pasar: sobre una
escena oscura y uniforme —la cámara tapada, por ejemplo— cualquier cristal se lee como
una caja gris. No es un defecto que se pueda corregir subiendo la intensidad; es la
naturaleza del material.

**Ojo: `BotaButton` no vale sobre cristal.** Sus tres estilos tiñen el contenido con el
verde de marca, calculado para contrastar sobre fondos **claros**. Sobre el velo oscuro
de la cámara ese verde se queda por debajo de AA y casi no se lee — se detectó en una
captura del dispositivo, no compilando. Las acciones que van sobre cristal usan
`BotaTheme.colors.onScrim` como color de contenido, con `headline` y 44 dp de área
táctil. Si esto se repite en más pantallas, merece un estilo propio en el design system.

**Composición sobre la cámara: los estados se turnan, no se acumulan.** La regla que
salió de ver la pantalla amontonada en el dispositivo:

- Con decisión visible → el marco de encuadre se apaga (no hay nada que encuadrar), la
  orientación desaparece y la entrada manual se repliega dentro de la tarjeta como
  «No es esto».
- Sin decisión → manda la orientación, el marco respira y la entrada manual está a mano.

Y la jerarquía de la decisión: **el material es antetítulo pequeño y la caneca es el
titular** (`title2`). Lo que hay que hacer con el residuo importa más que lo que el
modelo cree que es.

**Por qué no desenfoca el fondo.** `PreviewView` monta un `SurfaceView`, que el sistema
compone en una capa aparte y **no se puede capturar** en un `GraphicsLayer`: un desenfoque
sobre el visor no vería la cámara, vería un agujero. Es además la doctrina de Apple para
su variante *clear*, la que se usa sobre contenido rico en medios. El desenfoque real
llegará donde el fondo sí lo compone Compose —cabeceras sobre contenido con scroll—, y
pasar el visor a `TextureView` para desenfocarlo es una decisión de rendimiento de cámara
que corresponde a CAM, no a FRONT.

**Estado por material** (`BotaGlassState`)

- `Settled` — en reposo, con o sin decisión.
- `Analyzing` — el borde respira, lento y sin llamar la atención.
- `Uncertain` — el material se empaña y el borde pierde definición. Baja confianza es el
  flujo protagonista de la app, así que el cristal se ve **dudando, no fallando**.

### `BotaBottomSheet`

```kotlin
BotaBottomSheet(onDismissRequest = { visible = false }) {
    // contenido; asa y márgenes ya resueltos
}
```

Hoja modal con asa discreta, esquinas superiores de 28 dp y velo estándar. Es la superficie
para decisiones contextuales (p. ej. selección manual en baja confianza, CUS-006).

### `BotaActivityIndicator`

```kotlin
BotaActivityIndicator()                       // 20 dp, decorativo
BotaActivityIndicator(size = 36.dp, contentDescription = stringResource(R.string.loading))
```

Rueda de radios estilo iOS para esperas cortas e indeterminadas. Nunca a pantalla completa
ni bloqueando la vista de cámara.

### `BotaStatusPill`

```kotlin
BotaStatusPill(
    text = stringResource(R.string.confidence_high),
    tone = BotaStatusTone.Success,            // Neutral | Accent | Success | Warning | Error
)
```

Cápsula tintada para estados de confianza y avisos breves. El texto es obligatorio: el tono
refuerza, nunca sustituye, la información (RNF-010). Es el vehículo previsto para las
indicaciones discretas de captura de la pantalla de cámara (S06), que además respeta la
política anti-saturación de indicaciones (RF-018).

## Claro / oscuro

`BotaBienTheme` sigue el ajuste del sistema (`isSystemInDarkTheme()`). Los dos esquemas
comparten tokens: un componente bien escrito no pregunta por el tema salvo casos límite
(`BotaTheme.colors.isDark`). Los tonos semánticos cambian de variante entre esquemas para
mantener el contraste AA en ambos.
