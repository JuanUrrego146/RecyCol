/**
 * Pantalla de inicio: la misión de ahora y por qué importa.
 *
 * El motivo de que la misión ocupe casi toda la pantalla es de datos, no de
 * diseño: §10 dice que **el equilibrio entre clases importa más que el total**,
 * y la única forma de equilibrarlo es que la app pida lo que falta en vez de
 * esperar a que llegue. Enseñar cuánto falta y por qué es lo que hace que
 * alguien tome la siguiente foto.
 */

import { MATERIAL_INFO } from "../domain/materials";
import { missionHeadline, overallProgress, type Mission, type Tally } from "../domain/missions";
import type { ContributorState } from "../data/contributor";
import type { UploaderState } from "../data/useUploader";
import { Header, Notice, ProgressBar } from "./components";

export function HomeScreen({
  mission,
  tally,
  contributor,
  uploader,
  statsError,
  onStartMission,
  onStartFree,
  onRetryUploads,
}: {
  mission: Mission | null;
  tally: Tally;
  contributor: ContributorState;
  uploader: UploaderState;
  statsError: string | null;
  onStartMission: () => void;
  onStartFree: () => void;
  onRetryUploads: () => void;
}) {
  const overall = overallProgress(tally);
  const info = mission ? MATERIAL_INFO[mission.material] : null;

  return (
    <div className="screen">
      <Header
        right={
          <span className="tiny">
            {contributor.nickname ? `${contributor.nickname} · ` : ""}
            {contributor.contributed} {contributor.contributed === 1 ? "aporte" : "aportes"}
          </span>
        }
      />

      {mission && info ? (
        <div className="card">
          <span className="muted">Misión de ahora</span>
          <h1>
            <span aria-hidden="true">{info.glyph} </span>
            {missionHeadline(mission, info.name)}
          </h1>
          <p className="muted">{info.examples}</p>
          <ProgressBar
            ratio={mission.collected / mission.target}
            label={`Progreso de ${info.name}`}
          />
          <p className="tiny">
            {mission.collected} de {mission.target} fotos · faltan{" "}
            {Math.max(0, mission.target - mission.collected)}
          </p>
          <p className="muted">{mission.reason}</p>
        </div>
      ) : (
        <div className="card">
          <h1>Todas las misiones están cubiertas</h1>
          <p className="muted">
            Ya hay suficientes fotos de las once clases. Puedes seguir aportando en modo libre: lo
            que sobre se usará para el conjunto de control.
          </p>
        </div>
      )}

      <div className="stat-row">
        <div className="stat">
          <strong>{overall.collected.toLocaleString("es-CO")}</strong>
          <span className="tiny">fotos del proyecto</span>
        </div>
        <div className="stat">
          <strong>{Math.round(overall.ratio * 100)}%</strong>
          <span className="tiny">de la meta</span>
        </div>
      </div>

      {statsError && (
        <Notice tone="info">
          No se pudo consultar el avance del proyecto. Puedes aportar igual: las fotos se guardan
          aquí y suben cuando haya conexión.
        </Notice>
      )}

      {uploader.pending > 0 && (
        <Notice tone={uploader.stuck > 0 ? "warn" : "info"}>
          <div style={{ flex: 1 }}>
            {uploader.pending} {uploader.pending === 1 ? "foto pendiente" : "fotos pendientes"} de
            subir
            {uploader.sending ? " · subiendo…" : ""}
            {uploader.stuck > 0 && (
              <>
                <br />
                <span className="tiny">
                  {uploader.stuck} lleva(n) varios intentos fallidos. No se han perdido.
                </span>
              </>
            )}
          </div>
          {uploader.stuck > 0 && !uploader.sending && (
            <button type="button" className="button-ghost" onClick={onRetryUploads}>
              Reintentar
            </button>
          )}
        </Notice>
      )}

      <div className="actions">
        <button
          type="button"
          className="button button-primary button-block"
          onClick={onStartMission}
          disabled={!mission}
        >
          Aceptar misión
        </button>
        <button type="button" className="button button-secondary button-block" onClick={onStartFree}>
          Tengo otra cosa a mano
        </button>
      </div>
    </div>
  );
}
