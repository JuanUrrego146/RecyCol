/**
 * Pantalla de inicio: el menú de lo que más falta.
 *
 * El orden de la lista es de datos, no de diseño: §10 dice que **el equilibrio
 * entre clases importa más que el total**, así que arriba va lo que está más
 * lejos de su objetivo — hoy el cartón de bebidas, los electrónicos y el
 * residual (§7).
 *
 * Lo que decide el orden es nuestro; lo que se fotografía, de quien aporta. Una
 * misión impuesta tenía que adivinar qué tiene delante la persona que abre la
 * aplicación, y adivinaba mal casi siempre: pedía un vaso de café mientras
 * alguien estaba frente a una caneca de botellas, y la respuesta útil quedaba
 * detrás de un botón secundario. Con las cinco primeras a la vista, emparejar lo
 * que se ve con lo que falta es un toque, y ese toque arranca la cámara con el
 * material ya elegido: la intención sigue precediendo a la foto, que es lo que
 * hace que la etiqueta nazca limpia.
 */

import { useState } from "react";
import type { StoredProfile } from "../data/apiClient";
import { MATERIALS, MATERIAL_INFO, type Material } from "../domain/materials";
import { overallProgress, preferenceHint, type Mission, type Tally } from "../domain/missions";
import type { ContributorState } from "../data/contributor";
import type { UploaderState } from "../data/useUploader";
import { Header, Notice, ProgressBar } from "./components";

/**
 * Cuántas se enseñan sin desplegar.
 *
 * Cinco es lo que se recorre de una ojeada llevando algo en la otra mano. Con las
 * once desplegadas de entrada, la lista deja de ser «lo que más falta» y pasa a
 * ser un catálogo, que es precisamente lo que no orienta a nadie.
 */
const VISIBLE_NEEDS = 5;

export function HomeScreen({
  needs,
  tally,
  contributor,
  account,
  uploader,
  statsError,
  onStartMaterial,
  onStartFree,
  onRetryUploads,
  onOpenLogin,
  onOpenProfile,
}: {
  /** Lo que falta, ya ordenado por prioridad y avance. Vacío si todo está cubierto. */
  needs: readonly Mission[];
  tally: Tally;
  contributor: ContributorState;
  /** Perfil de la cuenta, o `null` si aporta de forma anónima. */
  account: StoredProfile | null;
  uploader: UploaderState;
  statsError: string | null;
  onStartMaterial: (material: Material) => void;
  onStartFree: () => void;
  onRetryUploads: () => void;
  onOpenLogin: () => void;
  onOpenProfile: () => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const overall = overallProgress(tally);
  const displayName = account?.fullName ?? contributor.nickname;

  // Al desplegar se ve la taxonomía entera: las que faltan en su orden, y detrás
  // las cubiertas. Se dejan tocables a propósito — que una clase esté cubierta no
  // es motivo para impedir aportar la que se tiene en la mano.
  const allCovered = needs.length === 0;
  const covered = MATERIALS.filter((material) => !needs.some((need) => need.material === material));
  const rows: readonly Row[] =
    expanded || allCovered
      ? [...needs.map(toRow), ...covered.map(coveredRow)]
      : needs.slice(0, VISIBLE_NEEDS).map(toRow);

  return (
    <div className="screen">
      <Header
        right={
          <button
            type="button"
            className="button-ghost tiny"
            onClick={account ? onOpenProfile : onOpenLogin}
          >
            {displayName ? `${displayName.split(" ")[0]} · ` : ""}
            {contributor.contributed} {contributor.contributed === 1 ? "aporte" : "aportes"}
          </button>
        }
      />

      <div className="card">
        {needs.length > 0 ? (
          <>
            <h1>Lo que más falta</h1>
            <p className="muted">Toca lo que tengas a mano y se abre la cámara.</p>
          </>
        ) : (
          <>
            <h1>Todas las clases están cubiertas</h1>
            <p className="muted">
              Ya hay suficientes fotos de las once. Puedes seguir aportando: lo que sobre se usará
              para el conjunto de control.
            </p>
          </>
        )}

        <ul className="need-list">
          {rows.map((row) => (
            <li key={row.material}>
              <button
                type="button"
                className="need-row"
                onClick={() => onStartMaterial(row.material)}
              >
                <span className="glyph" aria-hidden="true">
                  {MATERIAL_INFO[row.material].glyph}
                </span>
                <span className="need-text">
                  <span className="need-name">
                    {MATERIAL_INFO[row.material].name}
                    {row.hint && <span className="tiny"> · {row.hint}</span>}
                  </span>
                  <span className="tiny">{row.detail}</span>
                </span>
                <span
                  className={
                    row.missing === null ? "need-count need-count-covered" : "need-count"
                  }
                >
                  {row.missing === null ? "cubierto" : `faltan ${row.missing}`}
                </span>
              </button>
            </li>
          ))}
        </ul>

        {!allCovered && (
          <button
            type="button"
            className="button-ghost tiny"
            aria-expanded={expanded}
            onClick={() => setExpanded((current) => !current)}
          >
            {expanded ? "Ver solo lo que más falta" : `Ver los ${MATERIALS.length} materiales`}
          </button>
        )}
      </div>

      <ProgressBar ratio={overall.ratio} label="Progreso del proyecto" />
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

      {/*
        La invitación a entrar va aquí abajo y no como puerta de entrada: aportar
        sin cuenta tiene que seguir siendo el camino corto. Quien necesita que le
        cuenten los puntos sabe que los necesita.
      */}
      {!account && (
        <Notice tone="info">
          <div style={{ flex: 1 }}>
            ¿Tu profesor da puntos por esto? Entra con tu cuenta para que tus aportes queden a tu
            nombre.
          </div>
          <button type="button" className="button-ghost" onClick={onOpenLogin}>
            Entrar
          </button>
        </Notice>
      )}

      {/*
        La salida para lo que no se sabe qué es. Sigue existiendo aunque las once
        clases estén a un toque: en la lista hay que reconocer el material antes
        de disparar, y eso es justo lo que a veces no se sabe.
      */}
      <div className="actions">
        <button type="button" className="button button-secondary button-block" onClick={onStartFree}>
          No sé qué es: lo elijo después de la foto
        </button>
      </div>
    </div>
  );
}

interface Row {
  readonly material: Material;
  /** Cuántas faltan, o `null` si la clase ya llegó a su objetivo. */
  readonly missing: number | null;
  readonly hint: string | null;
  readonly detail: string;
}

function toRow(need: Mission): Row {
  return {
    material: need.material,
    missing: need.missing,
    hint: preferenceHint(need.preference),
    detail: need.reason,
  };
}

function coveredRow(material: Material): Row {
  return {
    material,
    missing: null,
    hint: null,
    detail: MATERIAL_INFO[material].examples,
  };
}
