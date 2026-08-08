/**
 * Pantalla de consentimiento. Es la primera y es obligatoria.
 *
 * Sin ella los datos no son utilizables comercialmente y esta plataforma
 * repetiría el problema que vino a resolver (issue #77). Por eso aceptar es un
 * acto explícito —un botón— y no un «al continuar aceptas» escondido debajo del
 * pliegue.
 */

import { useState } from "react";
import {
  CONSENT_ACTION,
  CONSENT_CLAUSES,
  CONSENT_SUMMARY,
  CONSENT_VERSION,
  CONTACT_EMAIL,
  DATA_CONTROLLER,
  hasContactChannel,
} from "../domain/consent";
import { Header, Notice } from "./components";

export function ConsentScreen({ onAccept }: { onAccept: (nickname: string) => void }) {
  const [nickname, setNickname] = useState("");

  return (
    <div className="screen">
      <Header />
      <h1>Enséñale a RecyCol a reconocer basura de verdad</h1>
      <p className="muted">{CONSENT_SUMMARY}</p>

      <div className="card">
        {CONSENT_CLAUSES.map((clause) => (
          <div key={clause.title}>
            <h2>{clause.title}</h2>
            <p className="muted">{clause.body}</p>
          </div>
        ))}
        <p className="tiny">
          Responsable: {DATA_CONTROLLER}. Versión {CONSENT_VERSION} de este aviso.
          {hasContactChannel() ? ` Contacto: ${CONTACT_EMAIL}.` : ""}
        </p>
      </div>

      {!hasContactChannel() && (
        <Notice tone="warn">
          Falta configurar el correo de contacto (<code>VITE_CONTACT_EMAIL</code>). Sin él no hay
          canal para ejercer el derecho de retirada.
        </Notice>
      )}

      <label className="choice-group">
        <span className="muted">Apodo (opcional, solo para la tabla de aportes)</span>
        <input
          className="chip"
          style={{ width: "100%" }}
          value={nickname}
          maxLength={24}
          placeholder="Como quieras que te llamemos"
          onChange={(event) => setNickname(event.target.value)}
        />
      </label>

      <div className="actions">
        <button type="button" className="button button-primary button-block" onClick={() => onAccept(nickname)}>
          {CONSENT_ACTION}
        </button>
        <p className="tiny">
          No fotografíes personas, matrículas ni documentos. Si sale alguien en la foto, no la
          envíes.
        </p>
      </div>
    </div>
  );
}
