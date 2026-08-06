# Catálogo de perfiles normativos

Cada perfil es un archivo JSON que valida contra `profile.schema.json` y se
registra en `catalog.json`. **Agregar un país o una variante institucional es
agregar un archivo y una entrada del índice; nunca tocar código Kotlin**
(RNF-004). La batería genérica de `CatalogEngineBatteryTest` y la validación de
`ProfileResourcesTest` cubren automáticamente todo perfil registrado.

El catálogo modela **país → institución**: cada país declara exactamente un
perfil por defecto (`"default": true`) y puede declarar variantes
institucionales adicionales.

## Perfiles registrados

| Id | País | Norma | Canecas |
|---|---|---|---|
| `co` | Colombia | Resolución 2184 de 2019 (Minambiente), vigente desde 2021 | blanca (aprovechables), negra (no aprovechables), verde (orgánicos) |
| `co-gtc24` | Colombia | GTC 24:2009 (Icontec), código multicorriente aún usado por universidades, hospitales e industria | gris (papel/cartón), azul (plásticos), blanca (vidrio), marrón oscuro (metales), crema (orgánicos), verde (ordinarios), roja (peligrosos) |
| `es` | España | Ley 7/2022 de residuos y suelos contaminados; sistema municipal de contenedores | azul (papel/cartón), amarillo (envases ligeros: plástico, metal, brik), verde (vidrio), marrón (orgánica), gris (resto), punto limpio (flujos especiales) |

## Fuentes consultadas

- Colombia, Resolución 2184 de 2019: código de colores blanco/negro/verde.
  Resumen: [kipclin.com](https://www.kipclin.com/blog/asesoria-en-limpieza/nuevo-codigo-de-colores-para-la-separacion-de-residuos-en-colombia.html).
- Colombia, GTC 24:2009: guía de separación en la fuente y su código
  multicorriente. Resúmenes: [implementandosgi.com](https://www.implementandosgi.com/normatividad/gtc-24/),
  [manosverdes.co](https://www.manosverdes.co/gtc-24-usos-y-recomendaciones/),
  [purabox.co](https://www.purabox.co/blogs/news/codigo-de-colores-para-separar-los-residuos-solidos-en-colombia).
- España, sistema de contenedores y Ley 7/2022: [Ecoembes](https://reducereutilizarecicla.org/colores-contenedores-de-reciclaje/),
  [climate.selectra.com](https://climate.selectra.com/es/reciclaje),
  [ambarplus.com](https://ambarplus.com/tipos-de-contenedores-reciclaje-espana/).

## Decisiones de datos

- En GTC 24 la caneca **verde** es la de ordinarios no aprovechables —lo
  contrario de la Resolución 2184, donde verde es orgánicos—. Esa inversión es
  exactamente lo que el diseño de perfiles debe absorber sin código.
- En España el amarillo recibe plástico, metal y brik a la vez: tres materiales
  de la taxonomía apuntan a la misma caneca, y el vidrio tiene contenedor
  propio. El vaso de café con recubrimiento va al amarillo solo si está vacío
  y sin residuo; con restos va a la fracción resto (gris).
- Los flujos sin contenedor de calle (textil, pilas, RAEE en España) se modelan
  como una caneca de ruta `SPECIAL_COLLECTION` («punto limpio»), que es como el
  usuario los encuentra en la práctica.
