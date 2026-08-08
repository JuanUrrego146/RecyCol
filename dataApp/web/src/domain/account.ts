/**
 * Cuentas de aportante.
 *
 * La versión 1 era deliberadamente anónima. Esto la cambia: Juan quiere hablar
 * con profesores de la UMNG para que den puntos por aportar fotos, y para eso
 * hace falta saber **quién** aportó cada una y a qué clase pertenece.
 *
 * Dos consecuencias que no son de código y conviene tener presentes:
 *
 * 1. Deja de ser un conjunto de datos anónimo. Nombre, correo, clase y profesor
 *    son datos personales de personas identificables, con las obligaciones que
 *    eso trae (finalidad declarada, autorización, y derechos de consulta y
 *    supresión). De ahí la versión 2.0 del consentimiento.
 * 2. **La cuenta es opcional.** Quien no quiera identificarse sigue aportando de
 *    forma anónima con un toque; simplemente no aparece en el informe del
 *    profesor. Obligar a registrarse es la barrera que más aportes mata, y el
 *    volumen es lo que el modelo necesita.
 *
 * Del lado bueno: una cuenta identifica a la persona aunque cambie de móvil, y
 * eso **refuerza** la partición por aportante que exige `CONTEXTO.md` §10. Con
 * identidad de navegador, la misma persona en dos dispositivos contaba como dos
 * aportantes.
 */

/** Dominio de correo de la Universidad Militar Nueva Granada. */
export const UMNG_EMAIL_DOMAIN = "unimilitar.edu.co";

export type Affiliation = "UMNG" | "GENERAL";

export interface AcademicInfo {
  /** Asignatura o clase. */
  readonly course: string;
  readonly group: string;
  readonly professor: string;
}

export interface Account {
  /** Identificador estable que da el proveedor de identidad. Es el `contributorId` de quien tiene cuenta. */
  readonly id: string;
  readonly provider: string;
  readonly email: string;
  readonly fullName: string;
  readonly affiliation: Affiliation;
  /**
   * `true` solo si la pertenencia a la UMNG está **comprobada** por el correo
   * con el que entró, no declarada a mano.
   *
   * Importa justo aquí: si un profesor va a dar puntos con esta lista, no puede
   * dar lo mismo «entró con su correo de la universidad» que «escribió que
   * estudia allí». El informe separa las dos cosas.
   */
  readonly academicVerified: boolean;
  readonly academic: AcademicInfo | null;
}

/**
 * ¿El correo con el que entró es de la UMNG?
 *
 * Acepta subdominios (`@est.unimilitar.edu.co` y similares) además del dominio
 * raíz, porque las cuentas de estudiante suelen colgar de uno.
 */
export function isUmngEmail(email: string): boolean {
  const at = email.lastIndexOf("@");
  if (at < 0) return false;
  const domain = email.slice(at + 1).trim().toLowerCase();
  return domain === UMNG_EMAIL_DOMAIN || domain.endsWith(`.${UMNG_EMAIL_DOMAIN}`);
}

export const MAX_NAME_LENGTH = 80;
export const MAX_ACADEMIC_FIELD_LENGTH = 60;

/** Rango de caracteres de control ASCII: no deben llegar a un nombre ni a un informe. */
const CONTROL_MAX = 0x1f;
const DELETE_CHAR = 0x7f;

/**
 * Limpia un texto escrito a mano: recorta, colapsa espacios y quita caracteres
 * de control. **No cambia mayúsculas**: hacerlo destrozaría apellidos como
 * «de la Cruz» o «Ríos-Peña».
 */
export function normalizeText(value: string, maxLength: number): string {
  const withoutControls = Array.from(value, (character) => {
    const code = character.codePointAt(0) ?? 0;
    return code <= CONTROL_MAX || code === DELETE_CHAR ? " " : character;
  }).join("");
  return withoutControls.replace(/\s+/g, " ").trim().slice(0, maxLength);
}

/**
 * Clave de agrupación para clases, grupos y profesores.
 *
 * Es el mismo problema que las etiquetas de material, a menor escala: escrito a
 * mano, un curso llega como «Cálculo 1», «calculo I» y «CALCULO 1», y el informe
 * del profesor sale partido en tres. La clave normaliza para **agrupar y
 * sugerir**; lo que se enseña sigue siendo lo que la persona escribió.
 */
export function canonicalKey(value: string): string {
  return normalizeText(value, 200)
    .toLowerCase()
    .normalize("NFD")
    // Marcas Unicode: separa la tilde de la letra y la descarta, sin escribir el
    // rango de combinantes a mano.
    .replace(/\p{M}/gu, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export interface ProfileDraft {
  readonly fullName: string;
  readonly affiliation: Affiliation;
  readonly academic: AcademicInfo | null;
}

export type ProfileProblem =
  | "NAME_REQUIRED"
  | "NAME_TOO_SHORT"
  | "COURSE_REQUIRED"
  | "GROUP_REQUIRED"
  | "PROFESSOR_REQUIRED";

/**
 * Valida el perfil. Devuelve la lista de problemas, vacía si está bien.
 *
 * Los campos académicos solo se exigen a quien declara ser de la UMNG: son lo
 * que permite al profesor encontrar a sus estudiantes en el informe, y sin ellos
 * el aporte cuenta pero no se le puede atribuir a nadie.
 */
export function validateProfile(draft: ProfileDraft): ProfileProblem[] {
  const problems: ProfileProblem[] = [];
  const name = normalizeText(draft.fullName, MAX_NAME_LENGTH);
  if (name.length === 0) problems.push("NAME_REQUIRED");
  else if (name.length < 3) problems.push("NAME_TOO_SHORT");

  if (draft.affiliation === "UMNG") {
    const academic = draft.academic;
    if (!academic || normalizeText(academic.course, MAX_ACADEMIC_FIELD_LENGTH).length === 0) {
      problems.push("COURSE_REQUIRED");
    }
    if (!academic || normalizeText(academic.group, MAX_ACADEMIC_FIELD_LENGTH).length === 0) {
      problems.push("GROUP_REQUIRED");
    }
    if (!academic || normalizeText(academic.professor, MAX_ACADEMIC_FIELD_LENGTH).length === 0) {
      problems.push("PROFESSOR_REQUIRED");
    }
  }
  return problems;
}

export function profileProblemMessage(problem: ProfileProblem): string {
  switch (problem) {
    case "NAME_REQUIRED":
      return "Escribe tu nombre completo.";
    case "NAME_TOO_SHORT":
      return "Ese nombre parece incompleto.";
    case "COURSE_REQUIRED":
      return "Escribe la clase o asignatura.";
    case "GROUP_REQUIRED":
      return "Escribe tu grupo.";
    case "PROFESSOR_REQUIRED":
      return "Escribe el nombre del profesor.";
  }
}

/** Deja el perfil listo para enviar: normalizado y sin campos académicos si no aplican. */
export function normalizeProfile(draft: ProfileDraft): ProfileDraft {
  const academic =
    draft.affiliation === "UMNG" && draft.academic
      ? {
          course: normalizeText(draft.academic.course, MAX_ACADEMIC_FIELD_LENGTH),
          group: normalizeText(draft.academic.group, MAX_ACADEMIC_FIELD_LENGTH),
          professor: normalizeText(draft.academic.professor, MAX_ACADEMIC_FIELD_LENGTH),
        }
      : null;
  return {
    fullName: normalizeText(draft.fullName, MAX_NAME_LENGTH),
    affiliation: draft.affiliation,
    academic,
  };
}
