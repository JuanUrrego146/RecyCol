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
import { readPrincipal, verifiesUmng, type ClientPrincipal } from "./session";
import type { ProfileDraft } from "../domain/account";

export interface SessionState {
  readonly loading: boolean;
  readonly principal: ClientPrincipal | null;
  readonly profile: StoredProfile | null;
  /** `true` si entró con el correo institucional de la UMNG. */
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

  const reload = useCallback(async () => {
    const principal = await readPrincipal();
    if (!principal) {
      setState({ ...INITIAL, loading: false });
      return;
    }
    try {
      const me = await fetchMe();
      setState({
        loading: false,
        principal,
        profile: me.profile ?? null,
        umngVerified: verifiesUmng(principal),
        accountContributorId: me.contributorId ?? null,
        error: null,
      });
    } catch (error) {
      // Hay identidad pero la API no responde. Se deja constancia y se sigue:
      // sin perfil confirmado se aporta de forma anónima antes que no aportar.
      setState({
        loading: false,
        principal,
        profile: null,
        umngVerified: verifiesUmng(principal),
        accountContributorId: null,
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
