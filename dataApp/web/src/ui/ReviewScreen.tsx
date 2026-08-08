/**
 * Moderación — la cuarentena de §10, punto 5.
 *
 * «Ninguna imagen entra al pool sin pasar por revisión: la app es pública y
 * llegará ruido, fotos irrelevantes y, con suerte, alguna imagen inapropiada.»
 * Esta pantalla es ese filtro, y es la razón por la que un enlace abierto es
 * asumible.
 *
 * Va en `/revisar` y la protege la autenticación integrada de Static Web Apps:
 * `staticwebapp.config.json` exige el rol `administrador`, así que el navegador
 * nunca llega aquí sin sesión. La API vuelve a comprobarlo por su cuenta — una
 * ruta protegida solo en el cliente no protege nada.
 *
 * Una a una y no en rejilla a propósito: revisar en cuadrícula lleva a aprobar
 * en bloque, y aprobar en bloque es cómo se cuela al pool la foto que no debía.
 */

import { useCallback, useEffect, useState } from "react";
import { fetchReviewQueue, submitReview, type ReviewQueueResponse } from "../data/apiClient";
import { MATERIAL_INFO } from "../domain/materials";
import { Header, Notice } from "./components";

type Item = ReviewQueueResponse["items"][number];

export function ReviewScreen() {
  const [items, setItems] = useState<readonly Item[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  const load = useCallback(async (continuation?: string) => {
    setLoading(true);
    setError(null);
    try {
      const page = await fetchReviewQueue(continuation);
      setItems((current) => (continuation ? [...current, ...page.items] : page.items));
      setCursor(page.continuationToken);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo cargar la cola");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const decide = async (item: Item, decision: "APPROVED" | "REJECTED", note?: string) => {
    setWorking(true);
    try {
      await submitReview(item.id, item.contributorId, decision, note);
      setItems((current) => current.filter((candidate) => candidate.id !== item.id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo registrar la decisión");
    } finally {
      setWorking(false);
    }
  };

  const current = items[0];

  return (
    <div className="screen">
      <Header right={<span className="tiny">Revisión</span>} />

      {error && <Notice tone="danger">{error}</Notice>}

      {!current && !loading && (
        <div className="card">
          <h1>No queda nada por revisar</h1>
          <p className="muted">Todas las capturas pendientes están resueltas.</p>
        </div>
      )}

      {loading && !current && <p className="muted">Cargando…</p>}

      {current && (
        <>
          <p className="tiny">
            {items.length} pendiente(s) cargada(s){cursor ? " · hay más" : ""}
          </p>
          <ReviewCard item={current} />
          <div className="actions">
            <button
              type="button"
              className="button button-primary button-block"
              disabled={working}
              onClick={() => void decide(current, "APPROVED")}
            >
              Aprobar
            </button>
            <div className="choice-row">
              <button
                type="button"
                className="button button-secondary"
                style={{ flex: 1 }}
                disabled={working}
                onClick={() => void decide(current, "REJECTED", "irrelevante")}
              >
                No es un residuo
              </button>
              <button
                type="button"
                className="button button-secondary"
                style={{ flex: 1 }}
                disabled={working}
                onClick={() => void decide(current, "REJECTED", "etiqueta incorrecta")}
              >
                Etiqueta mal
              </button>
            </div>
            <button
              type="button"
              className="button-ghost"
              disabled={working}
              onClick={() => void decide(current, "REJECTED", "inapropiada")}
            >
              Rechazar por contenido inapropiado
            </button>
            {cursor && !loading && (
              <button type="button" className="button-ghost" onClick={() => void load(cursor)}>
                Cargar más
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function ReviewCard({ item }: { item: Item }) {
  const info = MATERIAL_INFO[item.material];
  const fast = item.labelLatencyMs < 1000;
  const mismatched =
    item.requestedMaterial !== null && item.requestedMaterial !== item.material;

  return (
    <div className="card review-item">
      <img src={item.imageUrl} alt={`Captura etiquetada como ${info.name}`} />
      <h2>
        <span aria-hidden="true">{info.glyph} </span>
        {info.name}
        {item.contamination ? ` · ${item.contamination}` : ""}
      </h2>

      {fast && (
        <Notice tone="warn">
          Etiquetada en {item.labelLatencyMs} ms. Puede que no la mirara.
        </Notice>
      )}
      {mismatched && (
        <Notice tone="info">
          La misión pedía {MATERIAL_INFO[item.requestedMaterial!].name.toLowerCase()} y corrigió a{" "}
          {info.name.toLowerCase()}. Las correcciones valen más que las confirmaciones.
        </Notice>
      )}
      {!item.quality.accepted && (
        <Notice tone="warn">El filtro de calidad de la app habría rechazado este frame.</Notice>
      )}

      <dl className="field-list">
        <dt>Aportante</dt>
        <dd>
          <code>{item.contributorId.slice(0, 8)}</code>
        </dd>
        <dt>Objeto</dt>
        <dd>
          <code>{item.objectId.slice(0, 8)}</code>
        </dd>
        <dt>Luz / ángulo</dt>
        <dd>
          {item.light} / {item.angle}
        </dd>
        <dt>Estado / fondo</dt>
        <dd>
          {item.physicalState ?? "—"} / {item.background ?? "—"}
        </dd>
        <dt>Nitidez / luminancia</dt>
        <dd>
          {item.quality.sharpness.toFixed(3)} / {item.quality.luminance.toFixed(3)}
        </dd>
        <dt>pHash</dt>
        <dd>
          <code>{item.phash}</code>
        </dd>
        <dt>Nota</dt>
        <dd>{item.note ?? "—"}</dd>
      </dl>
    </div>
  );
}
