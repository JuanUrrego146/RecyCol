# Consentimiento y cesión de derechos — versión 2.0

**Responsable:** Juan Urrego, responsable del proyecto RecyCol.
**Fecha de entrada en vigor:** 07/08/2026.
**Aprobado por:** Juan, el 07/08/2026.
**Sustituye a:** [versión 1.0](CONSENT-v1.md), que **nunca llegó a producción**.

Texto archivado. La aplicación muestra el mismo contenido, repartido en pantallas
cortas, desde [`web/src/domain/consent.ts`](../web/src/domain/consent.ts).

---

## Por qué hubo que cambiar la versión 1.0

La 1.0 prometía literalmente: *«No pedimos tu nombre, tu correo ni ninguna
cuenta»*. Al añadir cuentas —para que profesores de la UMNG puedan reconocer los
aportes de sus estudiantes— eso deja de ser cierto para quien decide
identificarse.

**Un texto aceptado no se reinterpreta hacia atrás.** Así que sube la versión, se
vuelve a preguntar, y `ACCEPTED_CONSENT_VERSIONS` deja de admitir la 1.0. En este
caso el efecto práctico es nulo porque la plataforma no se había desplegado y no
hay ni un aporte hecho bajo aquel texto, pero el mecanismo queda ejercitado y es
el que hay que usar la próxima vez.

Y hay un cambio de fondo, no solo de redacción: con cuentas esto deja de ser un
conjunto de datos anónimo y pasa a haber **datos personales de personas
identificables**, la mayoría estudiantes. De ahí las cláusulas de derechos y la
advertencia explícita sobre lo que verá el profesor.

---

## Texto

**Qué se guarda de tus fotos.** La foto que tomes y la etiqueta que elijas, junto
con datos técnicos de la propia foto: nitidez, luz, ángulo, estado del objeto y
modelo aproximado del dispositivo.

**Puedes aportar sin identificarte.** Sin cuenta no pedimos tu nombre, tu correo
ni nada tuyo. Solo se guarda un código anónimo en este dispositivo, para no
mezclar tus fotos con las de otra persona.

**Si creas una cuenta.** Se guardan tu nombre completo y el correo con el que
entras. Si además dices ser de la Universidad Militar Nueva Granada, se guardan
la clase, el grupo y el nombre del profesor que indiques.

**Tu profesor verá cuánto aportaste.** Ese es justamente el propósito de dar esos
datos: que el profesor que indiques pueda ver tu nombre y cuántas fotos tuyas se
aprobaron, para reconocértelo. No verá quién eres si no creas cuenta.

**Nunca se guarda tu ubicación.** La aplicación ni siquiera la pide, tengas
cuenta o no. Tampoco accedemos a tu galería ni a tus contactos, y solo se usa la
cámara mientras estás tomando la foto.

**Para qué se usa.** Para entrenar y evaluar los modelos de clasificación de
residuos de RecyCol, incluido su uso comercial. RecyCol es un proyecto comercial
y preferimos decírtelo de frente.

**Qué autorizas.** Que Juan Urrego, responsable de RecyCol, use, reproduzca,
transforme y conserve las fotos que aportes con ese fin, sin límite de tiempo ni
de territorio y sin contraprestación económica.

**Qué nos aseguras.** Que la foto la tomaste tú, que no aparecen personas
reconocibles, ni matrículas, ni documentos, ni nada privado tuyo o de otra
persona.

**Tus derechos.** Puedes pedir en cualquier momento saber qué tenemos tuyo,
corregirlo o borrarlo. Con una salvedad honesta: si ya se entrenó un modelo con
tus fotos, podemos borrar las imágenes, pero no deshacer ese entrenamiento.

---

## Notas de aplicación

**Nunca escribimos una contraseña.** La autenticación la resuelve el proveedor
de identidad (Microsoft, GitHub) y la plataforma solo recibe el identificador, el
correo y el nombre. No hay credenciales que guardar ni que filtrar.

**Verificado contra declarado.** Entrar con un correo `@unimilitar.edu.co`
**acredita** la pertenencia a la UMNG; escribirla desde una cuenta personal la
**declara**. El informe al profesor distingue las dos, porque no es lo mismo si
de ello depende una nota.

**El profesor recibe lo mínimo.** Nombre, clase, grupo, fotos aprobadas, objetos
distintos y materiales distintos. No recibe las fotos, ni el correo de otros
estudiantes, ni nada de quien no lo haya indicado como profesor.

**Los datos personales no salen hacia ML.** El manifiesto que consume el
pipeline de entrenamiento lleva `contributor_id` y nada más: ni nombres, ni
correos, ni clase. La identidad sirve para agrupar y para reconocer, no para
entrenar.

**Canal de contacto.** Los derechos de consulta, actualización y supresión
necesitan una dirección donde ejercerlos. No está escrita en el repositorio: se
inyecta en el despliegue con `CONTACT_EMAIL`. Mientras falte, la aplicación
muestra un aviso. **Hay que configurarla antes de repartir el enlace**, y con
datos personales de por medio deja de ser una cortesía.

**Menores.** El texto no pregunta la edad. En un contexto universitario el riesgo
es bajo, pero si esto se difunde alguna vez en un colegio, la decisión hay que
revisarla **antes**, no después: ahí sí habría datos personales de menores.
