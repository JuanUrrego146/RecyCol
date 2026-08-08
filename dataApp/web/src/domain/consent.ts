/**
 * Consentimiento y cesión de derechos — versión 1.0.
 *
 * **Este es el motivo por el que la plataforma existe**, tanto como las fotos.
 * El pool actual depende en un ~70 % de Garbage Dataset v2, cuya cadena de
 * derechos no está acreditada, y eso **bloquea el lanzamiento comercial**
 * (issue #77, CONTEXTO.md §8). Una foto aportada aquí sin cesión explícita
 * repetiría exactamente el problema que se vino a resolver: sería una imagen más
 * sin permiso claro para usarla.
 *
 * Por eso el consentimiento es campo de **máxima prioridad** en §10, se guarda
 * **versionado** en cada captura, y sin él no se puede fotografiar nada.
 *
 * Si el texto cambia, **sube `CONSENT_VERSION`**: las capturas viejas conservan
 * la versión bajo la que se aportaron, y a quien ya aceptó se le vuelve a
 * preguntar. Nunca se reinterpreta hacia atrás lo que alguien aceptó.
 *
 * El texto completo, para archivo, está en `dataApp/docs/CONSENT-v1.md`.
 */

export const CONSENT_VERSION = "1.0";

/** Responsable del tratamiento. Aprobado por Juan el 07/08/2026. */
export const DATA_CONTROLLER = "Juan Urrego";

/**
 * Canal de contacto para ejercer el derecho de retirada. Se inyecta en el
 * despliegue (`VITE_CONTACT_EMAIL`) en vez de venir escrito en el repositorio.
 * `hasContactChannel()` permite avisar si falta antes de publicar.
 */
export const CONTACT_EMAIL: string = import.meta.env.VITE_CONTACT_EMAIL ?? "";

export function hasContactChannel(): boolean {
  return CONTACT_EMAIL.trim().length > 0;
}

export interface ConsentClause {
  readonly title: string;
  readonly body: string;
}

export const CONSENT_CLAUSES: readonly ConsentClause[] = [
  {
    title: "Qué se guarda",
    body:
      "La foto que tomes y la etiqueta que elijas, junto con datos técnicos de la propia foto: nitidez, " +
      "luz, ángulo, estado del objeto y modelo aproximado del dispositivo.",
  },
  {
    title: "Qué NO se guarda",
    body:
      "No pedimos tu nombre, tu correo ni ninguna cuenta. No se guarda tu ubicación: la aplicación " +
      "ni siquiera la pide. No accedemos a tu galería ni a tus contactos.",
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
    title: "Cómo te echas atrás",
    body:
      "Guardamos un código anónimo de aportante en este dispositivo. Con ese código puedes pedir que " +
      "borremos tus fotos. Con una salvedad honesta: si ya se entrenó un modelo con ellas, podemos " +
      "borrar las imágenes, pero no deshacer ese entrenamiento.",
  },
];

/** Compromiso corto, el que se lee de verdad. Encabeza la pantalla. */
export const CONSENT_SUMMARY =
  "Tus fotos van a enseñarle a RecyCol a reconocer basura real. Sin cuenta, sin tu ubicación y sin tu nombre.";

/** Frase del botón. Aceptar tiene que ser un acto explícito, no un scroll. */
export const CONSENT_ACTION = "Acepto y quiero aportar";
