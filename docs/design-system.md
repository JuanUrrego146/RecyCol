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

## Componentes

Todos en `com.botabien.android.ui.components`. Los textos llegan siempre por parámetro
desde un recurso de cadenas; ningún componente trae texto propio.

### `BotaButton`

```kotlin
BotaButton(
    text = stringResource(R.string.action_classify),
    onClick = { /* ... */ },
    style = BotaButtonStyle.Filled,   // Filled | Tinted | Plain, énfasis decreciente
    compact = false,                  // true → 44 dp para contextos densos
)
```

Regla de composición: **una sola acción `Filled` por pantalla**; el resto `Tinted` o `Plain`.

### `BotaCard`

```kotlin
BotaCard(onClick = { /* opcional: tarjeta pulsable */ }) {
    // contenido en columna; relleno lg por defecto
}
```

Superficie plana sin sombra. Se usa sobre `groupedBackground` para que el contraste de
fondos —no un borde— delimite la tarjeta.

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
