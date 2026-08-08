/**
 * Máquina de estados de la aplicación.
 *
 * consentimiento → inicio → cámara → etiquetado → recompensa → (cámara | inicio)
 *
 * Dos cosas que parecen detalles y no lo son:
 *
 * - **El `objectId` sobrevive entre tomas del mismo objeto.** §10 exige
 *   particionar por aportante «y después el objeto físico»; sin este
 *   identificador, cinco ángulos de la misma botella se reparten entre train y
 *   validación e inflan la métrica. Es el mismo error que ya se sospecha entre
 *   el train y la val actuales.
 * - **El recuento local se adelanta al servidor.** La misión avanza en cuanto se
 *   guarda el aporte, aunque la subida esté encolada. Si esperara a la
 *   confirmación, alguien sin cobertura vería la barra congelada y concluiría
 *   que no sirve de nada.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { CAPTURE_SCHEMA_VERSION, readDeviceInfo, type CaptureRecord } from "./domain/capture";
import { CONSENT_VERSION } from "./domain/consent";
import type { Material } from "./domain/materials";
import { EMPTY_TALLY, countsAsContaminated, rankNeeds, type Tally } from "./domain/missions";
import { fetchStats, linkAnonymousContributions } from "./data/apiClient";
import {
  acceptConsent,
  loadContributor,
  markAnonymousLinked,
  markUmngAsked,
  setClaimsAnonymous,
  needsConsent,
  randomId,
  recordContribution,
  setNickname,
  type ContributorState,
} from "./data/contributor";
import { logoutUrl } from "./data/session";
import { enqueue } from "./data/uploadQueue";
import { useSession } from "./data/useSession";
import { useUploader } from "./data/useUploader";
import { releaseCapturedImage, type CapturedImage } from "./capture/image";
import { CameraScreen } from "./ui/CameraScreen";
import { ConsentScreen } from "./ui/ConsentScreen";
import { HomeScreen } from "./ui/HomeScreen";
import { LabelScreen, type LabelDraft } from "./ui/LabelScreen";
import { AffiliationScreen } from "./ui/AffiliationScreen";
import { LoginScreen } from "./ui/LoginScreen";
import { ProfileScreen } from "./ui/ProfileScreen";
import { ReviewScreen } from "./ui/ReviewScreen";
import { RewardScreen } from "./ui/RewardScreen";

type Screen =
  | { readonly name: "home" }
  | { readonly name: "affiliation" }
  | { readonly name: "login" }
  | { readonly name: "profile" }
  | {
      readonly name: "camera";
      readonly requested: Material | null;
      readonly objectId: string;
      readonly sameObject: boolean;
      readonly prefill: Partial<LabelDraft> | null;
    }
  | {
      readonly name: "label";
      readonly image: CapturedImage;
      readonly requested: Material | null;
      readonly objectId: string;
      readonly prefill: Partial<LabelDraft> | null;
    }
  | {
      readonly name: "reward";
      readonly draft: LabelDraft;
      readonly objectId: string;
      readonly requested: Material | null;
    };

export function App() {
  // La moderación es otra aplicación en la práctica: otra ruta, otra audiencia y
  // sesión de administrador. Se separa aquí antes de montar nada del aporte.
  if (window.location.pathname.startsWith("/revisar")) {
    return (
      <div className="app">
        <ReviewScreen />
      </div>
    );
  }
  return <ContributeApp />;
}

function ContributeApp() {
  const [contributor, setContributor] = useState<ContributorState>(loadContributor);
  const session = useSession();
  const [screen, setScreen] = useState<Screen>({ name: "home" });
  const [serverTally, setServerTally] = useState<Tally>({});
  const [localTally, setLocalTally] = useState<Tally>({});
  const [statsError, setStatsError] = useState<string | null>(null);
  const uploader = useUploader();

  /**
   * Con quién se firman las capturas.
   *
   * Con cuenta es el identificador de la cuenta, que sobrevive al cambio de
   * móvil; sin ella, el UUID de este navegador. Ambos sirven para lo que §10
   * necesita —agrupar las fotos de una misma persona—, pero el de la cuenta lo
   * hace mejor: con identidad de navegador, la misma persona en dos dispositivos
   * contaba como dos aportantes.
   */
  const effectiveContributorId =
    session.profile !== null && session.accountContributorId !== null
      ? session.accountContributorId
      : contributor.id;

  const tally = useMemo(() => mergeTallies(serverTally, localTally), [serverTally, localTally]);
  const needs = useMemo(() => rankNeeds(tally), [tally]);

  const refreshStats = useCallback(async () => {
    try {
      const stats = await fetchStats();
      setServerTally(stats.tally);
      // El recuento del servidor ya incluye lo confirmado; el local solo tiene
      // sentido para lo que aún no ha subido.
      setLocalTally((current) => (uploader.pending === 0 ? {} : current));
      setStatsError(null);
    } catch (error) {
      setStatsError(error instanceof Error ? error.message : "Sin conexión con la API");
    }
  }, [uploader.pending]);

  useEffect(() => {
    void refreshStats();
  }, [refreshStats]);

  // Volvió de identificarse pero todavía no ha dicho quién es: sin nombre no se
  // le puede reconocer nada, así que se le pide antes de seguir. La salida
  // —aportar sin cuenta— sigue abierta desde esa misma pantalla.
  useEffect(() => {
    if (!session.loading && session.principal && !session.profile) {
      setScreen((current) => (current.name === "profile" ? current : { name: "profile" }));
    }
  }, [session.loading, session.principal, session.profile]);

  // Reintenta unir lo aportado sin cuenta cuando ya no queda nada en la cola.
  //
  // Al guardar el perfil el enlace puede fallar sin que sea un error: si las
  // capturas seguían encoladas, el aportante anónimo aún no existía en el
  // servidor. Se marca **solo** cuando el servidor confirma `linked`; darlo por
  // hecho antes perdería esas fotos para siempre y la misma persona contaría
  // como dos aportantes, que es justo lo que §10 prohíbe.
  useEffect(() => {
    const yaEnlazado = contributor.linkedAnonymousId === contributor.id;
    if (
      !contributor.claimsAnonymous ||
      session.loading ||
      !session.profile ||
      !session.accountContributorId ||
      session.accountContributorId === contributor.id ||
      yaEnlazado ||
      uploader.pending > 0
    ) {
      return;
    }
    let vigente = true;
    void linkAnonymousContributions(contributor.id)
      .then((resultado) => {
        if (vigente && resultado.linked) {
          setContributor((actual) => markAnonymousLinked(actual, actual.id));
        }
      })
      .catch(() => {
        // Sin red o sin sesión: se reintenta en la siguiente ocasión.
      });
    return () => {
      vigente = false;
    };
  }, [
    session.loading,
    session.profile,
    session.accountContributorId,
    contributor.id,
    contributor.linkedAnonymousId,
    contributor.claimsAnonymous,
    uploader.pending,
  ]);

  if (needsConsent(contributor)) {
    return (
      <div className="app">
        <ConsentScreen
          onAccept={(nickname) => {
            const withNickname = nickname.trim() ? setNickname(contributor, nickname) : contributor;
            setContributor(acceptConsent(withNickname));
            if (!contributor.umngAsked) setScreen({ name: "affiliation" });
          }}
        />
      </div>
    );
  }

  /**
   * Arranca la cámara para un material.
   *
   * `null` es el camino de quien no sabe qué tiene delante: se dispara primero y
   * se etiqueta después. Queda registrado como modo libre, que en el manifiesto
   * es lo que separa «lo elegí antes de disparar» de «lo reconocí después».
   */
  const startCapture = (material: Material | null) => {
    setScreen({
      name: "camera",
      requested: material,
      objectId: randomId(),
      sameObject: false,
      prefill: null,
    });
  };

  const saveDraft = async (
    draft: LabelDraft,
    image: CapturedImage,
    objectId: string,
    requested: Material | null,
  ) => {
    const record: CaptureRecord = {
      schemaVersion: CAPTURE_SCHEMA_VERSION,
      id: randomId(),
      contributorId: effectiveContributorId,
      objectId,
      consentVersion: CONSENT_VERSION,
      material: draft.material,
      contamination: draft.contamination,
      light: draft.light,
      angle: draft.angle,
      physicalState: draft.physicalState,
      background: draft.background,
      mode: requested === null ? "FREE" : "MISSION",
      requestedMaterial: requested,
      note: draft.note,
      labelLatencyMs: draft.labelLatencyMs,
      quality: {
        sharpness: image.quality.sharpness,
        luminance: image.quality.luminance,
        accepted: image.acceptedByProductionGate,
      },
      phash: image.phash,
      image: {
        width: image.width,
        height: image.height,
        bytes: image.blob.size,
        mimeType: image.blob.type || "image/jpeg",
      },
      crop: null,
      device: readDeviceInfo(),
      capturedAt: new Date().toISOString(),
    };

    await enqueue(record, image.blob);
    releaseCapturedImage(image);

    setLocalTally((current) => addToTally(current, draft.material, draft.contamination));
    setContributor(recordContribution(contributor));
    setScreen({ name: "reward", draft, objectId, requested });
    void uploader.flush();
  };

  switch (screen.name) {
    case "affiliation":
      return (
        <div className="app">
          <AffiliationScreen
            onUmng={() => {
              setContributor(markUmngAsked(contributor));
              setScreen({ name: "login" });
            }}
            onSkip={() => {
              setContributor(markUmngAsked(contributor));
              setScreen({ name: "home" });
            }}
          />
        </div>
      );

    case "login":
      return (
        <div className="app">
          <LoginScreen onSkip={() => setScreen({ name: "home" })} />
        </div>
      );

    case "profile":
      return (
        <div className="app">
          <ProfileScreen
            email={session.principal?.email ?? ""}
            umngVerified={session.umngVerified}
            anonymousCount={contributor.contributed}
            claimsAnonymous={contributor.claimsAnonymous}
            onClaimsAnonymousChange={(claims) =>
              setContributor((actual) => setClaimsAnonymous(actual, claims))
            }
            initial={
              session.profile
                ? {
                    fullName: session.profile.fullName,
                    affiliation: session.profile.affiliation,
                    academic: session.profile.academic,
                  }
                : null
            }
            saving={session.saving}
            error={session.error}
            onSave={(draft) => {
              // Se ofrece enlazar lo aportado antes de entrar: la misma persona
              // no puede contar como dos aportantes (§10).
              void session.save(draft, contributor.claimsAnonymous ? contributor.id : null).then((ok) => {
                if (ok) {
                  void refreshStats();
                  setScreen({ name: "home" });
                }
              });
            }}
            onSignOut={() => window.location.assign(logoutUrl())}
            onSkip={() => window.location.assign(logoutUrl())}
          />
        </div>
      );

    case "home":
      return (
        <div className="app">
          <HomeScreen
            needs={needs}
            tally={tally}
            contributor={contributor}
            account={session.profile}
            uploader={uploader}
            statsError={statsError}
            onStartMaterial={(material) => startCapture(material)}
            onStartFree={() => startCapture(null)}
            onRetryUploads={() => void uploader.retryAll()}
            onOpenLogin={() => setScreen({ name: "login" })}
            onOpenProfile={() => setScreen({ name: "profile" })}
          />
        </div>
      );

    case "camera":
      return (
        <div className="app">
          <CameraScreen
            requested={screen.requested}
            sameObject={screen.sameObject}
            onCaptured={(image) =>
              setScreen({
                name: "label",
                image,
                requested: screen.requested,
                objectId: screen.objectId,
                prefill: screen.prefill,
              })
            }
            onCancel={() => setScreen({ name: "home" })}
          />
        </div>
      );

    case "label":
      return (
        <div className="app">
          <LabelScreen
            image={screen.image}
            requested={screen.requested}
            prefill={screen.prefill}
            onSave={(draft) => void saveDraft(draft, screen.image, screen.objectId, screen.requested)}
            onRetake={() => {
              releaseCapturedImage(screen.image);
              setScreen({
                name: "camera",
                requested: screen.requested,
                objectId: screen.objectId,
                sameObject: screen.prefill !== null,
                prefill: screen.prefill,
              });
            }}
            onCancel={() => {
              releaseCapturedImage(screen.image);
              setScreen({ name: "home" });
            }}
          />
        </div>
      );

    case "reward":
      return (
        <div className="app">
          <RewardScreen
            material={screen.draft.material}
            contamination={screen.draft.contamination}
            requested={screen.requested}
            totalContributed={contributor.contributed}
            queued={uploader.pending > 0}
            onSameObject={() =>
              setScreen({
                name: "camera",
                // Misma pieza: se conserva el objeto y se reaprovechan las
                // respuestas, que son las mismas por definición.
                requested: screen.draft.material,
                objectId: screen.objectId,
                sameObject: true,
                prefill: screen.draft,
              })
            }
            onNext={() => {
              void refreshStats();
              // Otro objeto, misma clase: quien está junto a una caneca de
              // botellas tiene delante la segunda botella, no el siguiente hueco
              // de la lista. Para cambiar de clase está el menú, a un toque.
              startCapture(screen.requested);
            }}
            onHome={() => {
              void refreshStats();
              setScreen({ name: "home" });
            }}
          />
        </div>
      );
  }
}

function mergeTallies(left: Tally, right: Tally): Tally {
  const merged: Record<string, { total: number; contaminated: number }> = {};
  for (const source of [left, right]) {
    for (const [material, counts] of Object.entries(source)) {
      if (!counts) continue;
      const current = merged[material] ?? { total: 0, contaminated: 0 };
      merged[material] = {
        total: current.total + counts.total,
        contaminated: current.contaminated + counts.contaminated,
      };
    }
  }
  return merged as Tally;
}

function addToTally(
  tally: Tally,
  material: Material,
  contamination: LabelDraft["contamination"],
): Tally {
  const current = tally[material] ?? EMPTY_TALLY;
  return {
    ...tally,
    [material]: {
      total: current.total + 1,
      contaminated: current.contaminated + (countsAsContaminated(contamination) ? 1 : 0),
    },
  };
}
