/**
 * Estado de la sesión y del perfil, unificado.
 *
 * Junta dos fuentes que la aplicación necesita ver como una: la identidad que
 * resuelve Static Web Apps (`/.auth/me`) y el perfil que guardamos nosotros
 * (`/api/me`). La primera dice *quién entró*; la segunda, *qué nos contó sobre
 * sí mismo*.
 *
 * Puede faltar cualquiera de las dos, y ninguno de esos estados es un error:
 *
 * - Sin identidad → aportante anónimo. Es el camino por defecto.
 * - Con identidad y sin perfil → entró pero aún no dijo su nombre: hay que
 *   pedírselo antes de contar sus aportes.
 * - Con las dos → cuenta completa.
 */

import { useCallback, useEffect, useState } from "react";
import { fetchMe, saveProfile, type StoredProfile } from "./apiClient";
import type { ProfileDraft } from "../domain/account";

/** Identidad activa, tal y como la resuelve el servidor. */
export interface Identity {
  readonly email: string;
  readonly displayName: string | null;
  readonly provider: string;
}

export interface SessionState {
  readonly loading: boolean;
  readonly principal: Identity | null;
  readonly profile: StoredProfile | null;
  /** `true` si entró con el correo institucional de la UMNG. Lo decide el servidor. */
  readonly umngVerified: boolean;
  /** Identificador con el que se firman las capturas, o `null` si aún se está resolviendo. */
  readonly accountContributorId: string | null;
  readonly error: string | null;
}

export interface Session extends SessionState {
  readonly saving: boolean;
  readonly save: (draft: ProfileDraft, linkAnonymousId: string | null) => Promise<boolean>;
  readonly reload: () => Promise<void>;
}

const INITIAL: SessionState = {
  loading: true,
  principal: null,
  profile: null,
  umngVerified: false,
  accountContributorId: null,
  error: null,
};

export function useSession(): Session {
  const [state, setState] = useState<SessionState>(INITIAL);
  const [saving, setSaving] = useState(false);

  /**
   * Una sola petición a `/api/me`, que es la fuente de identidad de la
   * aplicación. No se consulta `/.auth/me`: en un Function App esa ruta no la
   * atiende el middleware de autenticación y devuelve el HTML de la propia
   * aplicación, con lo que toda sesión parecería anónima.
   */
  const reload = useCallback(async () => {
    try {
      const me = await fetchMe();
      if (!me.signedIn) {
        setState({ ...INITIAL, loading: false });
        return;
      }
      setState({
        loading: false,
        principal: {
          email: me.email ?? "",
          displayName: me.displayName ?? null,
          provider: me.provider ?? "aad",
        },
        profile: me.profile ?? null,
        umngVerified: me.umngVerified ?? false,
        accountContributorId: me.contributorId ?? null,
        error: null,
      });
    } catch (error) {
      // Sin respuesta de la API no se puede saber si hay sesión. Se sigue como
      // anónimo: aportar sin cuenta es preferible a no poder aportar.
      setState({
        ...INITIAL,
        loading: false,
        error: error instanceof Error ? error.message : "No se pudo leer tu perfil",
      });
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const save = useCallback(
    async (draft: ProfileDraft, linkAnonymousId: string | null) => {
      setSaving(true);
      try {
        const saved = await saveProfile(draft, linkAnonymousId);
        setState((current) => ({
          ...current,
          profile: saved.profile,
          accountContributorId: saved.contributorId,
          error: null,
        }));
        return true;
      } catch (error) {
        setState((current) => ({
          ...current,
          error: error instanceof Error ? error.message : "No se pudo guardar tu perfil",
        }));
        return false;
      } finally {
        setSaving(false);
      }
    },
    [],
  );

  return { ...state, saving, save, reload };
}
