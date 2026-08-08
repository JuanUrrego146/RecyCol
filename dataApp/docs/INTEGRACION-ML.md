# Llevar los datos aportados al pipeline de ML

Dirigido al agente **ML**. Describe qué produce esta plataforma y, sobre todo,
**qué no se puede hacer con ello**.

> ### 🔒 Lo único que no admite discusión
>
> **RealWaste sigue intocable.** Estos datos son **otra fuente**, con su propia
> entrada en `label_mapping.yaml` y en `DATA_LICENSES.md` **antes** de usarse. No
> se mezclan con el control, no lo amplían y no lo sustituyen
> (`CONTEXTO.md` §10, punto 1).

---

## Lo que produce la plataforma

Dos cosas, ambas detrás del rol `administrador`:

```bash
# 1 · Manifiesto de lo APROBADO (JSONL por defecto, o ?format=csv)
curl -H "Cookie: <sesión>" "https://<host>/api/export/manifest?format=csv" > manifiesto.csv

# 2 · Firma temporal de lectura para bajar las imágenes
curl -H "Cookie: <sesión>" "https://<host>/api/export/sas?minutes=120"
# devuelve { url, minutes, hint } — `hint` trae el azcopy ya montado
azcopy copy "<url>" "./ml/data/recycol_aporta" --recursive
```

Las imágenes quedan organizadas como `MATERIAL/contributor_id/capture_id.jpg`, y
`relative_path` del manifiesto apunta ahí exactamente.

Ambas rutas paginan: el manifiesto devuelve la página siguiente en la cabecera
`x-continuation-token`, y se pasa como `?cursor=…`. Vacía cuando no queda nada.

**Solo sale lo aprobado.** Lo pendiente de revisión no se exporta: la cuarentena
no sería cuarentena si el exportador la saltara.

## Las dos columnas que hay que mirar antes que la etiqueta

`contributor_id` y `object_id`.

> «Partir por aportante, no por imagen. Si la misma persona fotografía su botella
> cinco veces y esas fotos caen unas en train y otras en validación, la métrica
> queda inflada — es el mismo error que ya sospechamos entre train y val. **La
> unidad de partición es el usuario, y después el objeto físico.**»
> — `CONTEXTO.md` §10, punto 2

Un `train_test_split` por fila sobre este manifiesto reproduce, dentro de los
datos propios, exactamente el problema que se vino a diagnosticar. Agrupa por
`contributor_id`; dentro de un aportante, `object_id` agrupa las tomas de la
misma pieza física desde ángulos distintos.

## El control propio ya viene reservado

La columna `split` trae `TRAIN` o `CONTROL`, **asignado por persona en el momento
de registrarse y nunca recalculado**. Es el segundo control propio que §10 pide
reservar desde el primer día: en torno al 15 % de los aportantes, congelado, que
**jamás entrena**.

Importa por lo que hoy falta: RealWaste no contiene `BEVERAGE_CARTON`, `BATTERY`
ni `ELECTRONIC`, así que **el caso estrella del producto no tiene control de
dominio real**. Este lo da.

- **No recalcules el reparto.** Si un aportante cruza de lado, deja de haber
  garantía de que el control venga de personas ausentes del entrenamiento, que es
  lo único que hace que ese control signifique algo.
- **No mezcles `CONTROL` con RealWaste.** Son dos controles distintos: uno mide
  dominio degradado real de relleno sanitario, otro mide el dominio de la app.
  Reportar contra los dos por separado dice más que promediarlos.

## Antes de entrenar con esto

1. **Deduplicar contra todo lo existente, RealWaste incluido.** El manifiesto
   trae `phash` calculado en el navegador, pero es un **prefiltro**: el
   redimensionado del navegador no es el de Pillow y unos bits pueden diferir.
   La deduplicación que decide es la de S22, con su propia herramienta.
2. **Registrar la fuente** en `ml/DATA_LICENSES.md` antes de usarla. Procedencia:
   aportaciones propias con cesión explícita, versión de consentimiento en la
   columna `consent_version`, responsable Juan Urrego. Veredicto comercial: apto
   — es justamente el punto de todo esto.
3. **Añadir la entrada a `ml/taxonomy/label_mapping.yaml`.** El mapeo es la
   identidad: las etiquetas ya salen en la taxonomía cerrada de once clases del
   dominio, no hay ninguna traducción que hacer. Eso es a propósito: aquí no hay
   texto libre que mapear a mano.
4. **Medir el efecto por separado.** Entrenar con y sin los datos propios y
   comparar **contra el control de siempre**. Es la única forma de saber cuánto
   aportan, y evita repetir la lección de `full-v2`, donde una fuente nueva
   mejoró la val interna mientras empeoraba lo que importa.
5. **Receta validada**, sin novedades: `--exclude garbage_dataset_v2:RESIDUAL`,
   sin palancas de coste. Y la regla de §7: **una diferencia menor de ~2 pp entre
   runs no significa nada.**

## Columnas que sirven para diagnosticar, no para entrenar

| Columna | Para qué |
|---|---|
| `contamination` | **El dato más valioso del manifiesto.** Contaminación real etiquetada por una persona, en las tres clases de fibra. Es lo que la síntesis de S26 no logró replicar y no existe en ninguna fuente pública |
| `sharpness`, `luminance`, `quality_accepted` | Métricas del **mismo filtro que la app de producción** (`FrameQualityThresholds.kt`, replicado en el navegador). Permiten separar «el modelo falla en frames que la app aceptaría» de «falla en frames que el filtro habría rechazado» |
| `light`, `angle` | Etiquetan el dominio: permiten medir **dónde** falla el modelo, no solo que falla |
| `physical_state`, `background` | `background` sirve para comprobar si el modelo se apoya en el fondo como atajo, que es lo que pasó con la síntesis |
| `corrected` | `true` cuando la persona corrigió lo que pedía la misión. §10 punto 4: **la corrección vale más que la confirmación**. Priorízalas en el muestreo |
| `fast_label`, `label_latency_ms` | `true` si etiquetó en menos de un segundo. §10 punto 6: es señal de no haber mirado. Baja la confianza de esas etiquetas |
| `device_platform`, `device_memory_gb` | Diagnóstico de sesgo por cámara y por gama |

`note` no se exporta al manifiesto de entrenamiento **a propósito**: es texto
libre y §10 lo limita a descubrir clases que faltan, no a entrenar. Se lee desde
la pantalla de moderación.

## Sobre las cuentas de aportante

Desde el 07/08 quien quiere puede identificarse, para que un profesor de la UMNG
le reconozca los aportes. Para el pipeline eso cambia poco y a mejor:

- `contributor_id` empieza por `acc-` cuando la persona tiene cuenta. **Sigue
  siendo la unidad de partición**, y ahora es mejor que antes: una cuenta
  identifica a la persona aunque cambie de móvil, mientras que el identificador
  de navegador la duplicaba.
- Quien aportó primero sin cuenta y luego entró queda **unido**: sus capturas
  anónimas cuentan como suyas. Si los dos lados habían caído en particiones
  distintas, **todo pasa a `TRAIN`** —incluidas las capturas ya guardadas—, nunca
  al revés. Ante la duda, jamás control.
- **El manifiesto no lleva ni un dato personal.** Ni nombres, ni correos, ni
  clase, ni profesor. Eso vive en el informe académico, que es otra ruta, con
  otro permiso y otro propósito. La identidad sirve para agrupar y para
  reconocer; no para entrenar.

## Lo que no vas a encontrar

- **Geolocalización.** No se captura. Ni campo, ni permiso, ni petición al
  navegador (§10 la descarta por riesgo de privacidad sin retorno técnico).
- **Metadatos EXIF.** El reencodificado en canvas los elimina por construcción.
- **Recortes.** `crop` es siempre nulo en la versión 1: se guarda la foto
  completa, porque guardar solo el recorte es irreversible.
- **Datos personales.** `contributor_id` es un UUID generado en el navegador. No
  hay nombres, ni correos, ni cuentas.

## Cuánto hace falta para que esto mueva la aguja

De la tabla de §10, y es lo que los objetivos de las misiones ya persiguen:

| Objetivo | Volumen | Qué compra |
|---|---|---|
| Desbloquear el caso estrella | 300–500 de `BEVERAGE_CARTON`, la mitad con restos | El vaso de café pasa de no verificable a verificable |
| Reactivar la etapa 2 | 400–600 de cartón y papel con contaminación etiquetada | Convierte la contaminación de sintética a real |
| Cubrir `ELECTRONIC` | 300–500 | Cierra la única clase a cero |
| Mínimo para aprender algo | ~150 por clase | Permite **fine-tuning** sobre el modelo actual |

La aplicación deja de pedir una clase al llegar a su objetivo. El tope por clase
vale más que duplicar el volumen: el pool actual ya está sesgado.
