/**
 * Consentimiento y cesión de derechos — versión 2.0.
 *
 * **Este es el motivo por el que la plataforma existe**, tanto como las fotos.
 * El pool de entrenamiento actual depende en un ~70 % de Garbage Dataset v2,
 * cuya cadena de derechos no está acreditada, y eso **bloquea el lanzamiento
 * comercial** (issue #77, `CONTEXTO.md` §8). Una foto aportada aquí sin cesión
 * explícita repetiría exactamente el problema que se vino a resolver.
 *
 * ## Por qué la versión 2.0
 *
 * La 1.0 prometía literalmente «no pedimos tu nombre, tu correo ni ninguna
 * cuenta». Al añadir cuentas —para que los profesores de la UMNG puedan dar
 * puntos por aportar— eso deja de ser cierto para quien decide identificarse, y
 * **un texto aceptado no se reinterpreta hacia atrás**: sube la versión y se
 * vuelve a preguntar.
 *
 * El texto ahora distingue dos caminos, porque la aplicación tiene dos:
 *
 * - **Sin cuenta**: sigue sin recogerse un solo dato personal. Es el camino por
 *   defecto y no ha cambiado nada.
 * - **Con cuenta**: nombre, correo y —si dice ser de la UMNG— clase, grupo y
 *   profesor. Con una obligación que se cumple diciéndola de frente: **el
 *   profesor va a ver cuántas fotos aportó cada quien**, y eso hay que
 *   advertirlo antes, no después.
 *
 * El texto completo, para archivo, está en `dataApp/docs/CONSENT-v2.md`.
 */

export const CONSENT_VERSION = "2.0";

/** Responsable del tratamiento. Aprobado por Juan el 07/08/2026. */
export const DATA_CONTROLLER = "Juan Urrego";

/**
 * Canal de contacto para ejercer los derechos de consulta, actualización y
 * supresión. Se inyecta en el despliegue (`VITE_CONTACT_EMAIL`) en vez de venir
 * escrito en el repositorio.
 *
 * Con datos personales de por medio deja de ser una cortesía: es el canal por el
 * que alguien pide que borres sus datos.
 */
export const CONTACT_EMAIL: string = import.meta.env.VITE_CONTACT_EMAIL ?? "";

export function hasContactChannel(): boolean {
  return CONTACT_EMAIL.trim().length > 0;
}

export interface ConsentClause {
  readonly title: string;
  readonly body: string;
  /** `true` si solo aplica a quien crea una cuenta. La interfaz lo marca. */
  readonly accountOnly?: boolean;
}

export const CONSENT_CLAUSES: readonly ConsentClause[] = [
  {
    title: "Qué se guarda de tus fotos",
    body:
      "La foto que tomes y la etiqueta que elijas, junto con datos técnicos de la propia foto: nitidez, " +
      "luz, ángulo, estado del objeto y modelo aproximado del dispositivo.",
  },
  {
    title: "Puedes aportar sin identificarte",
    body:
      "Sin cuenta no pedimos tu nombre, tu correo ni nada tuyo. Solo se guarda un código anónimo en este " +
      "dispositivo, para no mezclar tus fotos con las de otra persona.",
  },
  {
    title: "Si creas una cuenta",
    accountOnly: true,
    body:
      "Se guardan tu nombre completo y el correo con el que entras. Si además dices ser de la Universidad " +
      "Militar Nueva Granada, se guardan la clase, el grupo y el nombre del profesor que indiques.",
  },
  {
    title: "Tu profesor verá cuánto aportaste",
    accountOnly: true,
    body:
      "Ese es justamente el propósito de dar esos datos: que el profesor que indiques pueda ver tu nombre y " +
      "cuántas fotos tuyas se aprobaron, para reconocértelo. No verá quién eres si no creas cuenta.",
  },
  {
    title: "Nunca se guarda tu ubicación",
    body:
      "La aplicación ni siquiera la pide, tengas cuenta o no. Tampoco accedemos a tu galería ni a tus " +
      "contactos, y solo se usa la cámara mientras estás tomando la foto.",
  },
  {
    title: "Para qué se usa",
    body:
      "Para entrenar y evaluar los modelos de clasificación de residuos de RecyCol, incluido su uso " +
      "comercial. RecyCol es un proyecto comercial y preferimos decírtelo de frente.",
  },
  {
    title: "Qué autorizas",
    body:
      `Que ${DATA_CONTROLLER}, responsable de RecyCol, use, reproduzca, transforme y conserve las fotos ` +
      "que aportes con ese fin, sin límite de tiempo ni de territorio y sin contraprestación económica.",
  },
  {
    title: "Qué nos aseguras",
    body:
      "Que la foto la tomaste tú, que no aparecen personas reconocibles, ni matrículas, ni documentos, " +
      "ni nada privado tuyo o de otra persona.",
  },
  {
    title: "Tus derechos",
    body:
      "Puedes pedir en cualquier momento saber qué tenemos tuyo, corregirlo o borrarlo. Con una salvedad " +
      "honesta: si ya se entrenó un modelo con tus fotos, podemos borrar las imágenes, pero no deshacer " +
      "ese entrenamiento.",
  },
];

/** Compromiso corto, el que se lee de verdad. Encabeza la pantalla. */
export const CONSENT_SUMMARY =
  "Tus fotos van a enseñarle a RecyCol a reconocer basura real. Puedes aportar sin dar tu nombre; " +
  "la cuenta solo hace falta si quieres que tu profesor lo vea.";

/** Frase del botón. Aceptar tiene que ser un acto explícito, no un scroll. */
export const CONSENT_ACTION = "Acepto y quiero aportar";
