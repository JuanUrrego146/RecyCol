# RecyCol Aporta

Plataforma web de recolección de dataset propio. Es la fase **RecyCol
Entrenamiento** de [`CONTEXTO.md`](../CONTEXTO.md) §10.

**No es la app de RecyCol.** Es un componente aparte, en su propia carpeta, con su
propio despliegue y —esto importa— **su propio modelo de privacidad**: aquí las
imágenes sí salen del dispositivo, porque ese es el propósito. Por eso no
comparte una línea de código de persistencia con la app principal: nadie debe
heredar por accidente el permiso de subir fotos.

## El problema que resuelve

El modelo acierta el 98,6 % de ruta en su propio dominio y **el 74,2 % contra
residuos reales**. Esa distancia no es de arquitectura —más capacidad la
empeora— ni de cuantización: es que los datasets públicos son fotos de estudio y
la app se usa con un móvil sobre basura real.

Esta plataforma produce fotos del dominio exacto de destino, con derechos limpios
y de las clases que faltan. Ataca las tres cosas a la vez:

| Problema abierto | Cómo lo ataca |
|---|---|
| Brecha de dominio (§7) — la única que queda | Fotos de móvil sobre residuos reales, no de estudio |
| Riesgo legal de Garbage v2 (#77) — bloquea el lanzamiento comercial | Cesión explícita y versionada en cada foto |
| `BEVERAGE_CARTON` con ~100 imágenes, `ELECTRONIC` a cero | Misiones que piden justamente esas clases |
| Contaminación sintética que no transfiere (S26) | Estado de contaminación real, etiquetado por personas, en fibra |

## Cómo funciona para quien aporta

```
enlace → consentimiento (una vez) → MISIÓN: "faltan 240 vasos de bebida"
   → foto → "¿es cartón de bebidas?" → "¿queda líquido dentro?" → luz y ángulo
   → "va a la CANECA NEGRA, porque lleva recubrimiento de polietileno"
```

Tres decisiones de diseño que no son cosméticas:

**La app pide la clase que falta, en vez de esperar a que llegue.** §10 dice que
el equilibrio entre clases pesa más que el total. Cuando la app pide «un vaso de
café», la persona sabe qué está fotografiando antes de disparar: la intención
precede a la foto, la etiqueta nace limpia y no hay sugerencia que aceptar sin
mirar. Y la clase deja de pedirse al llegar a su objetivo.

**La recompensa es la app principal funcionando.** Después de etiquetar —nunca
antes, eso sesgaría— se muestra a qué caneca va y por qué, leyendo el perfil
normativo real (`shared/resources/profiles/co.json`). Quien aporta un dato
aprende algo. Es lo que hace que tome la segunda foto.

**Se etiqueta desde una lista cerrada de once materiales.** Nada de texto libre
como vía principal: produce «botella», «botella de plástico», «plastico» y «PET»
para el mismo objeto. El campo de texto existe solo como matiz y no entrena.

## Cuentas: opcionales, y para qué sirven

Se puede aportar sin identificarse, y ese sigue siendo el camino por defecto:
obligar a registrarse es la barrera que más aportes mata.

La cuenta existe para una cosa concreta: **que un profesor de la UMNG pueda ver
cuánto aportó cada estudiante y reconocérselo**. Quien entra da su nombre y, si
es de la universidad, su clase, su grupo y el profesor.

- **No manejamos contraseñas.** La autenticación la resuelve Static Web Apps con
  proveedores ya integrados. Nada que cifrar, nada que recuperar, nada que
  filtrar.
- **Entrar con el correo `@unimilitar.edu.co` acredita** la pertenencia a la
  UMNG. Declararla desde una cuenta personal la deja como «declarado», y el
  informe lo distingue: no es lo mismo si de ello depende una nota.
- **La identidad la manda la sesión, no el cuerpo de la petición.** Sin eso,
  cualquiera podría atribuirle fotos a otro estudiante — para inflarle el conteo
  o para ensuciárselo.
- **El informe cuenta fotos aprobadas, objetos distintos y materiales
  distintos.** Dar puntos crea el incentivo de inflar el número; treinta fotos de
  la misma lata son un objeto.
- **Los datos personales no salen hacia ML.** El manifiesto de entrenamiento
  lleva `contributor_id` y nada más.

Google requiere el plan Standard de Azure (9 USD/mes) y por eso llega desactivado,
listo en `LOGIN_PROVIDERS`. Microsoft y GitHub van en el plan gratuito.

## Qué se guarda, y qué no

Todos los campos 🔴 y 🟠 de la tabla de §10, más los 🟡:

etiqueta de material · **estado de contaminación** (obligatorio en papel, cartón
y cartón de bebidas) · consentimiento y su versión · **foto sin recortar** ·
luz · ángulo · estado físico · fondo · nitidez y exposición medidas con el mismo
filtro que la app real · gama y plataforma del dispositivo · tiempo de respuesta
al etiquetar · pHash · identificador de aportante y de objeto físico.

**Sin geolocalización.** Ni campo, ni permiso, ni petición al navegador. **Sin
cuentas ni datos personales**: el aportante es un UUID generado en el navegador.
Reencodificar en canvas elimina además cualquier metadato EXIF.

## Estructura

```
dataApp/
├── web/            PWA en React + Vite + TypeScript, sin más dependencias
│   ├── domain/     taxonomía, misiones, contaminación, perfil normativo
│   ├── capture/    cámara, filtro de calidad, pHash, codificación
│   ├── data/       aportante local, cola offline en IndexedDB, cliente de API
│   └── ui/         pantallas
├── api/            Azure Functions (Node 20) sobre Cosmos DB y Blob Storage
├── infra/          aprovisionamiento con el CLI de Azure
└── docs/           despliegue, consentimiento e integración con ML
```

## Arrancar

```bash
npm ci --prefix dataApp/web && npm run dev --prefix dataApp/web   # http://localhost:5173
npm test --prefix dataApp/web                                     # 72 pruebas
npm test --prefix dataApp/api                                     # 33 pruebas
```

La cámara funciona en `localhost` sin HTTPS. Sin la API levantada se puede
aportar igual: las capturas se encolan en el navegador, que es el mismo camino
que se recorre sin cobertura.

Para desplegar: [`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md).
Para llevar los datos a ML: [`docs/INTEGRACION-ML.md`](docs/INTEGRACION-ML.md).

## Cosas que conviene saber antes de tocar esto

- **El filtro de calidad tiene tres réplicas** que deben decir lo mismo:
  `FrameQualityThresholds.kt` (la app, fuente de verdad),
  `ml/quality/frame_quality_gate.py` (el pipeline) y
  `web/src/capture/qualityGate.ts` (aquí). `qualityGate.test.ts` usa los mismos
  patrones sintéticos que el autochequeo de la réplica en NumPy.
- **El perfil normativo se importa, no se copia.** `domain/profile.ts` lee
  `shared/resources/profiles/co.json`, que es de ámbito RULES. Un cambio
  incompatible allí se pone rojo en el CI de `dataApp`, no en producción.
- **La partición es por aportante y luego por objeto.** Es lo único de aquí que
  no admite atajos: un `split` por imagen infla la métrica y destruye la
  evidencia. Está explicado en `docs/INTEGRACION-ML.md`.
- **Ningún secreto vive en el repositorio.** Las cadenas de conexión se cargan
  con `az staticwebapp appsettings set` desde el script de aprovisionamiento.
- **La moderación es obligatoria.** Toda captura nace en `PENDING`. El enlace es
  público y va a llegar ruido; el exportador solo ve lo aprobado.

## Estado

Construido y probado en local. **Sin desplegar todavía**: falta ejecutar los seis
pasos de `docs/DESPLIEGUE.md`, que requieren una sesión de Azure en la máquina de
Juan.

Pendiente para una versión 2, cuando ML exporte los modelos a ONNX: **que el
modelo proponga y la persona corrija**. Hoy no se puede —los `.tflite` son INT8
con firma `[1,3,lado,lado]` NCHW y no corren en navegador—, y además el modo
misión resuelve mejor el equilibrio por clase. Cuando llegue, cada corrección
será aprendizaje activo sobre el punto exacto donde el modelo falla.
