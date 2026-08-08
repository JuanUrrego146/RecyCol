/**
 * Identidad del aportante — anónima, local y estable.
 *
 * §10 exige poder **particionar por aportante, no por imagen**: si la misma
 * persona fotografía su botella cinco veces y esas fotos caen unas en train y
 * otras en validación, la métrica queda inflada. Hace falta, entonces, saber qué
 * fotos vienen de la misma persona.
 *
 * Lo que **no** hace falta es saber quién es esa persona. Así que no hay cuentas,
 * ni correos, ni contraseñas: solo un UUID generado en el navegador y guardado
 * en `localStorage`. Da la trazabilidad que la partición necesita con cero datos
 * personales, y de paso quita la barrera que más aportes mata — registrarse.
 *
 * Limitación asumida: la misma persona en dos dispositivos cuenta como dos
 * aportantes, y borrar los datos del navegador crea uno nuevo. Para lo que la
 * partición necesita —que las fotos de un mismo objeto y una misma sesión no se
 * repartan entre train y control— es suficiente.
 */

import type { Material } from "../domain/materials";
import { CONSENT_VERSION } from "../domain/consent";

const STORAGE_KEY = "recycol.aporta.contributor.v1";

export interface ContributorState {
  readonly id: string;
  /** Apodo opcional, solo para la tabla de aportes. Nunca obligatorio. */
  readonly nickname: string | null;
  /** Versión del consentimiento aceptada, o `null` si todavía no aceptó. */
  readonly consentVersion: string | null;
  readonly consentAcceptedAt: string | null;
  /** Aportes confirmados por el servidor. Para la racha y los logros. */
  readonly contributed: number;
  /** Últimas clases pedidas por la misión, para no repetir siempre la misma. */
  readonly recentMissions: readonly Material[];
  /** `true` cuando ya se preguntó si es de la UMNG. Se pregunta una sola vez. */
  readonly umngAsked: boolean;
  /**
   * Identificador anónimo que el **servidor ya confirmó** haber unido a la
   * cuenta.
   *
   * Se guarda solo con la confirmación, nunca con «la petición no falló». Si el
   * enlace no se hizo —por ejemplo porque las capturas seguían en la cola y el
   * aportante anónimo aún no existía en el servidor— hay que reintentarlo, y
   * marcarlo antes de tiempo lo perdería para siempre y en silencio. Eso rompería
   * la partición por persona que exige §10 sin que nada avisara.
   */
  readonly linkedAnonymousId: string | null;
  readonly createdAt: string;
}

const RECENT_MISSIONS_KEPT = 10;

function newContributor(): ContributorState {
  return {
    id: randomId(),
    nickname: null,
    consentVersion: null,
    consentAcceptedAt: null,
    contributed: 0,
    recentMissions: [],
    umngAsked: false,
    linkedAnonymousId: null,
    createdAt: new Date().toISOString(),
  };
}

/** `crypto.randomUUID` no existe en contextos no seguros; el respaldo evita romper en desarrollo. */
function randomId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function loadContributor(): ContributorState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return persist(newContributor());
    const parsed = JSON.parse(raw) as Partial<ContributorState>;
    if (typeof parsed.id !== "string" || parsed.id.length === 0) return persist(newContributor());
    return {
      id: parsed.id,
      nickname: typeof parsed.nickname === "string" ? parsed.nickname : null,
      consentVersion: typeof parsed.consentVersion === "string" ? parsed.consentVersion : null,
      consentAcceptedAt:
        typeof parsed.consentAcceptedAt === "string" ? parsed.consentAcceptedAt : null,
      contributed: typeof parsed.contributed === "number" ? parsed.contributed : 0,
      recentMissions: Array.isArray(parsed.recentMissions)
        ? (parsed.recentMissions as Material[])
        : [],
      umngAsked: parsed.umngAsked === true,
      linkedAnonymousId:
        typeof parsed.linkedAnonymousId === "string" ? parsed.linkedAnonymousId : null,
      createdAt: typeof parsed.createdAt === "string" ? parsed.createdAt : new Date().toISOString(),
    };
  } catch {
    // localStorage puede estar bloqueado (modo privado de algunos navegadores).
    // Se sigue con un aportante en memoria antes que dejar la app inservible.
    return newContributor();
  }
}

export function persist(state: ContributorState): ContributorState {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Sin persistencia el aporte sigue funcionando; solo se pierde la racha.
  }
  return state;
}

export function acceptConsent(state: ContributorState): ContributorState {
  return persist({
    ...state,
    consentVersion: CONSENT_VERSION,
    consentAcceptedAt: new Date().toISOString(),
  });
}

/**
 * `true` si hay que volver a pedir el consentimiento: o no lo aceptó nunca, o lo
 * aceptó bajo una versión anterior del texto.
 */
export function needsConsent(state: ContributorState): boolean {
  return state.consentVersion !== CONSENT_VERSION;
}

export function setNickname(state: ContributorState, nickname: string): ContributorState {
  const trimmed = nickname.trim().slice(0, 24);
  return persist({ ...state, nickname: trimmed.length > 0 ? trimmed : null });
}

export function recordMissionShown(state: ContributorState, material: Material): ContributorState {
  const recent = [...state.recentMissions, material].slice(-RECENT_MISSIONS_KEPT);
  return persist({ ...state, recentMissions: recent });
}

export function recordContribution(state: ContributorState): ContributorState {
  return persist({ ...state, contributed: state.contributed + 1 });
}

/** La pregunta de adscripción se hace una sola vez, no en cada visita. */
export function markUmngAsked(state: ContributorState): ContributorState {
  return persist({ ...state, umngAsked: true });
}

/** Solo se llama con la confirmación del servidor. Ver `linkedAnonymousId`. */
export function markAnonymousLinked(state: ContributorState, anonymousId: string): ContributorState {
  return persist({ ...state, linkedAnonymousId: anonymousId });
}

export { randomId };
