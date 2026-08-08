# Dónde está la base de datos y cómo descargarla para entrenar

Respuesta corta:

```bash
export PATH="/c/Program Files/Microsoft SDKs/Azure/CLI2/wbin:$PATH"   # solo en Windows
bash dataApp/infra/export.sh
```

Eso deja las fotos y sus etiquetas en `ml/data/recycol_aporta/`, listas para el
pipeline. Tarda lo que tarde la descarga y no pide nada más que tener `az login`
hecho.

---

## Dónde vive cada cosa

Todo está en **una sola cuenta de almacenamiento**, `strecycolaporta94b924`,
dentro del grupo `rg-recycol-aporta`. No hay servidor de base de datos que
administrar ni que pagar.

| Qué | Dónde | Cómo verlo |
|---|---|---|
| **Las fotos** | Blob Storage, contenedor `captures` | `az storage blob list --container-name captures` |
| **Las etiquetas y metadatos** | Table Storage, tabla `captures` | `az storage entity query --table-name captures` |
| Quién aportó qué | Table Storage, tabla `contributors` | igual, cambiando el nombre |
| Contadores de las misiones | Table Storage, tabla `counters` | igual |
| Cola de moderación | Table Storage, tabla `pendingreview` | igual |

Las fotos se guardan como `MATERIAL/aportante/captura.jpg`, así que el propio
nombre de la ruta ya dice de qué clase es cada una.

El contenedor es **privado**. Nadie puede abrir una foto con la URL a pelo: la
aplicación emite firmas temporales para subir, y la pantalla de moderación otra
para mirar. Se lee entero solo desde tu sesión de `az`, que es lo que hace el
script.

## Qué hace el script

```bash
bash dataApp/infra/export.sh [carpeta-destino]
```

1. Descarga todas las imágenes con `az storage blob download-batch`.
2. Vuelca las etiquetas y metadatos desde Table Storage.
3. Escribe un resumen con lo que hay que mirar antes de entrenar.

Deja esto:

```
ml/data/recycol_aporta/
├── images/MATERIAL/<aportante>/<captura>.jpg
├── manifest.csv        una fila por foto, con relative_path apuntando a images/
├── manifest.jsonl      lo mismo, línea a línea
└── RESUMEN.txt         recuentos y avisos
```

**Solo sale lo aprobado.** Lo que está en cuarentena no se exporta: la revisión
no sería revisión si el exportador la saltara. Para inspeccionar lo pendiente,
`ESTADO=PENDING bash dataApp/infra/export.sh /tmp/revision` — pero eso no entrena.

> **No hace falta `azcopy`**, que no está instalado en tu máquina, ni tener sesión
> abierta en la aplicación web. Es la razón por la que existe este script: la ruta
> `/api/export/manifest` funciona, pero **solo desde el navegador con tu sesión de
> administrador**, porque la identidad la inyecta la plataforma en una cabecera y
> desde la terminal no hay forma de reproducirla. Sirve para echar un vistazo
> rápido; para descargar de verdad, el script.

## Las tres cosas que no puedes hacer mal

Están explicadas en `CONTEXTO.md` §10 y el resumen las repite cada vez, porque
equivocarse aquí destruye evidencia que costó año y medio.

**1. Particiona por persona, nunca por foto.** Usa la columna
`canonical_contributor_id`. Si repartes las filas al azar entre entrenamiento y
validación, las cinco fotos que alguien tomó de su misma botella caen unas a cada
lado, el modelo ve en validación casi lo mismo que vio entrenando, y la métrica
sale inflada. Es exactamente el error que ya se sospecha entre el train y la val
actuales. Dentro de una persona, `object_id` agrupa las tomas del mismo objeto
físico.

**2. Lo marcado `CONTROL` jamás entrena.** Es un segundo conjunto de control
propio, congelado, de personas que no aparecen en entrenamiento. Existe porque
RealWaste no contiene `BEVERAGE_CARTON`, `BATTERY` ni `ELECTRONIC`: hoy el vaso
de café —el caso estrella del producto— no tiene ninguna forma de verificarse.
Este se la da. En cuanto entrene, deja de servir para eso.

**3. RealWaste sigue intocable.** Esto es **otra fuente**, con su propia entrada
en `ml/DATA_LICENSES.md` y en `ml/taxonomy/label_mapping.yaml` antes de usarse. No
se mezcla con el control, no lo amplía y no lo sustituye.

El resumen avisa solo si detecta que alguien aparece a la vez en `TRAIN` y en
`CONTROL`. Si sale ese aviso, no entrenes hasta arreglarlo.

## Las columnas y para qué sirve cada una

Las etiquetas de material están en `material`, y ya vienen en la taxonomía cerrada
de once clases del dominio: **no hay nada que mapear a mano**, que es justo el
problema que el texto libre habría creado.

Lo demás sirve para diagnosticar por qué falla el modelo, no solo para entrenar:

| Columna | Para qué |
|---|---|
| `contamination` | **Lo más valioso del manifiesto.** Contaminación real etiquetada por una persona, en papel, cartón y cartón de bebidas. Es lo que la síntesis de S26 no logró replicar y no existe en ninguna fuente pública |
| `sharpness`, `luminance`, `quality_accepted` | Métricas del **mismo filtro que la app de producción**. Permiten separar «el modelo falla en fotos que la app aceptaría» de «falla en fotos que el filtro habría rechazado» |
| `light`, `angle` | Etiquetan el dominio: dejan medir **dónde** falla, no solo que falla |
| `background` | Sirve para comprobar si el modelo se apoya en el fondo como atajo, que es lo que pasó con la síntesis |
| `corrected` | La persona corrigió lo que pedía la misión. §10: **la corrección vale más que la confirmación**; son los casos donde el modelo se equivoca |
| `fast_label`, `label_latency_ms` | Etiquetó en menos de un segundo: señal de no haber mirado. Bájale la confianza |
| `split` | `TRAIN` o `CONTROL`, asignado **por persona** al registrarse. No lo recalcules |

**No hay ni un dato personal en el manifiesto.** Ni nombres, ni correos, ni clase,
ni profesor. Eso vive en el informe académico, que es otra ruta y otro propósito.
`canonical_contributor_id` es un identificador opaco.

## Meterlo en el pipeline de ML

1. Descarga con el script a `ml/data/recycol_aporta/`.
2. Registra la fuente en `ml/DATA_LICENSES.md`: aportaciones propias con cesión
   explícita, versión del consentimiento en la columna `consent_version`,
   responsable Juan Urrego, veredicto comercial **apto** — que es el punto entero
   de todo esto.
3. Añade la entrada a `ml/taxonomy/label_mapping.yaml`. El mapeo es la identidad.
4. Deduplica con pHash contra el pool **y contra RealWaste**. El manifiesto trae
   `phash`, pero es un prefiltro: el redimensionado del navegador no es el de
   Pillow y unos bits pueden diferir. La deduplicación que decide es la de S22.
5. Entrena **con y sin** estos datos y compara **contra el control de siempre**.
   Es la única forma de saber cuánto aportan, y evita repetir la lección de
   `full-v2`, donde una fuente nueva mejoró la validación interna mientras
   empeoraba lo que importa.

## Cuánto hace falta para que esto sirva de algo

De la tabla de §10, que es lo que persiguen las misiones de la aplicación:

| Objetivo | Volumen | Qué compra |
|---|---|---|
| Desbloquear el caso estrella | 300–500 de `BEVERAGE_CARTON`, la mitad con restos | El vaso de café pasa de no verificable a verificable |
| Reactivar la contaminación | 400–600 de cartón y papel con estado etiquetado | La convierte de sintética a real |
| Cubrir `ELECTRONIC` | 300–500 | Cierra la única clase a cero |
| Mínimo para aprender algo | ~150 por clase | Permite **ajuste fino** sobre el modelo actual |

Con menos de ~150 por clase no merece la pena reentrenar: mira el `RESUMEN.txt`
antes de gastar horas de GPU.

## Copia de seguridad

El script es también la copia de seguridad: descárgalo de vez en cuando. Si
alguna vez se borra el grupo de recursos —o si Microsoft suspendiera la
suscripción de estudiante— lo único que sobrevive es lo que tengas en disco.
