# Consentimiento y cesión de derechos — versión 1.0

**Responsable:** Juan Urrego, responsable del proyecto RecyCol.
**Fecha de entrada en vigor:** 07/08/2026.
**Aprobado por:** Juan, el 07/08/2026.

Este es el texto archivado de la versión 1.0. La aplicación muestra el mismo
contenido, redactado en pantallas cortas, desde
[`web/src/domain/consent.ts`](../web/src/domain/consent.ts).

> **Por qué esto existe y por qué es lo primero que ve quien aporta.** El pool de
> entrenamiento actual depende en un ~70 % de un dataset cuya cadena de derechos
> no está acreditada, y eso bloquea el lanzamiento comercial de RecyCol (issue
> #77, `CONTEXTO.md` §8). Una foto recogida aquí sin cesión explícita sería una
> imagen más sin permiso claro para usarla: exactamente el problema que esta
> plataforma vino a resolver.

---

## Texto

**Qué se guarda.** La foto que tomes y la etiqueta que elijas, junto con datos
técnicos de la propia foto: nitidez, luz, ángulo, estado del objeto y modelo
aproximado del dispositivo.

**Qué no se guarda.** No pedimos tu nombre, tu correo ni ninguna cuenta. No se
guarda tu ubicación: la aplicación ni siquiera la pide. No accedemos a tu galería
ni a tus contactos.

**Para qué se usa.** Para entrenar y evaluar los modelos de clasificación de
residuos de RecyCol, incluido su uso comercial. RecyCol es un proyecto comercial
y preferimos decírtelo de frente.

**Qué autorizas.** Que Juan Urrego, responsable de RecyCol, use, reproduzca,
transforme y conserve las fotos que aportes con ese fin, sin límite de tiempo ni
de territorio y sin contraprestación económica.

**Qué nos aseguras.** Que la foto la tomaste tú, que no aparecen personas
reconocibles, ni matrículas, ni documentos, ni nada privado tuyo o de otra
persona.

**Cómo te echas atrás.** Guardamos un código anónimo de aportante en este
dispositivo. Con ese código puedes pedir que borremos tus fotos. Con una salvedad
honesta: si ya se entrenó un modelo con ellas, podemos borrar las imágenes, pero
no deshacer ese entrenamiento.

---

## Notas de aplicación

**Versionado.** Cada captura guarda la versión del consentimiento bajo la que se
aportó (`consentVersion`). Si el texto cambia, sube `CONSENT_VERSION` en
`consent.ts` y añade la nueva versión a `ACCEPTED_CONSENT_VERSIONS` en
`api/src/model.ts`. A quien ya había aceptado se le vuelve a preguntar. **Nunca
se reinterpreta hacia atrás lo que alguien aceptó** — retirar una versión de
`ACCEPTED_CONSENT_VERSIONS` deja de admitir aportes nuevos bajo ella, pero no
cambia el permiso de los que ya entraron.

**Canal de contacto.** El derecho de retirada necesita una dirección donde
ejercerlo. No está escrita en el repositorio: se inyecta en el despliegue con la
variable `CONTACT_EMAIL`. Mientras falte, la aplicación muestra un aviso en la
pantalla de consentimiento. **Hay que configurarla antes de repartir el enlace.**

**Alcance de esta versión.** Cubre las fotos aportadas por la propia persona
desde la cámara del navegador. No cubre subida de archivos desde galería, que por
eso no existe en la aplicación: una imagen de galería puede traer coordenadas en
los metadatos, ser de otra persona o venir de internet.

**Menores.** El texto no pregunta la edad y la aplicación no la registra: pedirla
sería recoger un dato personal para proteger contra un riesgo que la propia
naturaleza del aporte —una foto de basura, sin personas— mantiene bajo. Si en
algún momento se difunde en un entorno escolar, esta decisión hay que revisarla
antes, no después.
