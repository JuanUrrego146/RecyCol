/**
 * Recompensa: a qué caneca va lo que acabas de fotografiar, y por qué.
 *
 * Esto es lo que hace que alguien tome la segunda foto. No es una animación de
 * confeti: es la aplicación principal de RecyCol funcionando. La persona aporta
 * un dato y a cambio aprende algo que probablemente no sabía —que el vaso de
 * café va a la negra y no a la blanca—, con la justificación normativa que sale
 * del mismo perfil que usa la app real.
 *
 * **Se enseña después de etiquetar, nunca antes.** Enseñarlo antes convertiría la
 * respuesta en una pista y sesgaría la etiqueta, que es justo lo que esta
 * plataforma vino a evitar.
 */

import { REGULATION_NAME, resolveBin } from "../domain/profile";
import type { ContaminationState } from "../domain/contamination";
import { MATERIAL_INFO, type Material } from "../domain/materials";
import { Header, Notice } from "./components";

export function RewardScreen({
  material,
  contamination,
  totalContributed,
  queued,
  onSameObject,
  onNext,
  onHome,
}: {
  material: Material;
  contamination: ContaminationState | null;
  totalContributed: number;
  queued: boolean;
  onSameObject: () => void;
  onNext: () => void;
  onHome: () => void;
}) {
  const decision = resolveBin(material, contamination);
  const info = MATERIAL_INFO[material];

  return (
    <div className="screen">
      <Header
        right={
          <button type="button" className="button-ghost" onClick={onHome}>
            Inicio
          </button>
        }
      />

      <p className="muted">Aporte guardado {queued ? "· sube solo cuando haya red" : ""}</p>

      <div className="card">
        <span className="muted">
          <span aria-hidden="true">{info.glyph} </span>
          {info.name} · va a
        </span>
        <div className="bin-badge">
          <span
            className="bin-swatch"
            style={{ background: decision.colorHex }}
            aria-hidden="true"
          />
          <h1>{decision.displayName}</h1>
        </div>
        {decision.degraded && (
          <Notice tone="warn">
            Por estar contaminado cambia de caneca. Ese detalle es exactamente lo que estamos
            enseñándole al modelo.
          </Notice>
        )}
        <p className="muted">{decision.justification}</p>
        <p className="tiny">Según la {REGULATION_NAME}.</p>
      </div>

      <div className="stat-row">
        <div className="stat">
          <strong>{totalContributed}</strong>
          <span className="tiny">
            {totalContributed === 1 ? "foto aportada" : "fotos aportadas"}
          </span>
        </div>
      </div>

      <div className="actions">
        <button type="button" className="button button-secondary button-block" onClick={onSameObject}>
          Otra foto del mismo objeto
        </button>
        <button type="button" className="button button-primary button-block" onClick={onNext}>
          Siguiente misión
        </button>
        <p className="tiny">
          Varias fotos del mismo objeto desde ángulos distintos valen mucho: enseñan que es la misma
          cosa vista de otra forma.
        </p>
      </div>
    </div>
  );
}
