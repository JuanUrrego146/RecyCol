/**
 * Etiquetado: la pantalla donde se decide si el dato vale algo.
 *
 * Reglas que vienen de §10 y que aquí se hacen visibles:
 *
 * - **Nunca texto libre como vía principal.** El material se elige de la lista
 *   cerrada de once. El campo de texto existe solo como matiz y no entrena.
 * - **La misión propone, la persona dispone.** Si aceptó buscar cartón de
 *   bebidas, se le pregunta si eso es lo que fotografió, y decir que no es un
 *   toque. Lo que se guarda es lo que responde, no lo que se le pidió; ambas
 *   cosas quedan registradas para poder comparar.
 * - **El estado de contaminación manda en fibra.** En papel, cartón y cartón de
 *   bebidas no se puede continuar sin responderlo: es el dato que la síntesis no
 *   logró replicar y que no existe en ninguna fuente pública.
 * - **Se cronometra la respuesta.** Confirmar en menos de un segundo es señal de
 *   no haber mirado, y ML necesita poder bajarle la confianza a esas etiquetas.
 */

import { useEffect, useMemo, useRef, useState } from "react";
import type { CapturedImage } from "../capture/image";
import { rejectionReason } from "../capture/qualityGate";
import {
  ANGLE_OPTIONS,
  BACKGROUND_OPTIONS,
  LIGHT_OPTIONS,
  MAX_NOTE_LENGTH,
  PHYSICAL_STATE_OPTIONS,
  type Angle,
  type Background,
  type LightCondition,
  type PhysicalState,
} from "../domain/capture";
import {
  CONTAMINATION_OPTIONS,
  contaminationPolicy,
  contaminationQuestion,
  type ContaminationState,
} from "../domain/contamination";
import { MATERIALS, MATERIAL_INFO, type Material } from "../domain/materials";
import { ChoiceGroup, Header, Notice } from "./components";

export interface LabelDraft {
  readonly material: Material;
  readonly contamination: ContaminationState | null;
  readonly light: LightCondition;
  readonly angle: Angle;
  readonly physicalState: PhysicalState | null;
  readonly background: Background | null;
  readonly note: string | null;
  readonly labelLatencyMs: number;
}

export function LabelScreen({
  image,
  requested,
  prefill,
  onSave,
  onRetake,
  onCancel,
}: {
  image: CapturedImage;
  requested: Material | null;
  /** Respuestas de la toma anterior del mismo objeto: repetirlas a mano no aporta nada. */
  prefill: Partial<LabelDraft> | null;
  onSave: (draft: LabelDraft) => void;
  onRetake: () => void;
  onCancel: () => void;
}) {
  const shownAt = useRef(performance.now());
  const [material, setMaterial] = useState<Material | null>(prefill?.material ?? null);
  const [askingMaterial, setAskingMaterial] = useState(requested === null && !prefill?.material);
  const [contamination, setContamination] = useState<ContaminationState | null>(
    prefill?.contamination ?? null,
  );
  const [light, setLight] = useState<LightCondition | null>(prefill?.light ?? null);
  const [angle, setAngle] = useState<Angle | null>(prefill?.angle ?? null);
  const [physicalState, setPhysicalState] = useState<PhysicalState | null>(
    prefill?.physicalState ?? null,
  );
  const [background, setBackground] = useState<Background | null>(prefill?.background ?? null);
  const [note, setNote] = useState(prefill?.note ?? "");
  const [showDetail, setShowDetail] = useState(false);

  // Si cambia el material, la respuesta de contaminación anterior deja de aplicar.
  useEffect(() => {
    if (material && contaminationPolicy(material) === "NOT_ASKED") setContamination(null);
  }, [material]);

  const policy = material ? contaminationPolicy(material) : "NOT_ASKED";
  const qualityProblem = image.acceptedByProductionGate ? null : rejectionReason(image.quality);

  const complete = useMemo(() => {
    if (!material || !light || !angle) return false;
    if (policy === "REQUIRED" && contamination === null) return false;
    return true;
  }, [material, light, angle, policy, contamination]);

  const save = () => {
    if (!material || !light || !angle) return;
    onSave({
      material,
      contamination,
      light,
      angle,
      physicalState,
      background,
      note: note.trim().length > 0 ? note.trim().slice(0, MAX_NOTE_LENGTH) : null,
      labelLatencyMs: Math.round(performance.now() - shownAt.current),
    });
  };

  return (
    <div className="screen">
      <Header
        right={
          <button type="button" className="button-ghost" onClick={onCancel}>
            Descartar
          </button>
        }
      />

      <div className="viewfinder" style={{ aspectRatio: `${image.width} / ${image.height}` }}>
        <img src={image.previewUrl} alt="La foto que acabas de tomar" />
      </div>

      {qualityProblem && (
        <Notice tone="warn">
          <div style={{ flex: 1 }}>
            {qualityProblem} La aplicación real pediría otra toma. Puedes enviarla igual —también
            enseña— pero si puedes, repítela.
          </div>
          <button type="button" className="button-ghost" onClick={onRetake}>
            Repetir
          </button>
        </Notice>
      )}

      {/* Paso 1 · material */}
      {requested && !askingMaterial && material === null ? (
        <div className="card">
          <h2>¿Es {MATERIAL_INFO[requested].name.toLowerCase()}?</h2>
          <p className="muted">{MATERIAL_INFO[requested].examples}</p>
          {MATERIAL_INFO[requested].hint && (
            <p className="tiny">{MATERIAL_INFO[requested].hint}</p>
          )}
          <div className="choice-row">
            <button
              type="button"
              className="button button-primary"
              style={{ flex: 1 }}
              onClick={() => setMaterial(requested)}
            >
              Sí, es esto
            </button>
            <button
              type="button"
              className="button button-secondary"
              style={{ flex: 1 }}
              onClick={() => setAskingMaterial(true)}
            >
              No, es otra cosa
            </button>
          </div>
        </div>
      ) : (
        <MaterialPicker
          value={material}
          onChange={(value) => {
            setMaterial(value);
            setAskingMaterial(false);
          }}
          expanded={askingMaterial || material === null}
          onExpand={() => setAskingMaterial(true)}
        />
      )}

      {/* Paso 2 · contaminación */}
      {material && policy !== "NOT_ASKED" && (
        <div className="card">
          <h2>{contaminationQuestion(material)}</h2>
          {policy === "REQUIRED" && (
            <p className="tiny">
              Este dato es el más valioso de todos: no existe en ninguna fuente pública.
            </p>
          )}
          <ChoiceGroup
            legend="Estado"
            options={CONTAMINATION_OPTIONS.map((option) => ({
              value: option.state,
              name: option.name,
              glyph: option.glyph,
            }))}
            value={contamination}
            onChange={setContamination}
          />
          {policy === "OPTIONAL" && (
            <button type="button" className="button-ghost" onClick={() => setContamination(null)}>
              Prefiero no decirlo
            </button>
          )}
        </div>
      )}

      {/* Paso 3 · condiciones de la toma */}
      {material && (
        <div className="card">
          <ChoiceGroup legend="¿Con qué luz?" options={LIGHT_OPTIONS} value={light} onChange={setLight} />
          <ChoiceGroup legend="¿Desde dónde?" options={ANGLE_OPTIONS} value={angle} onChange={setAngle} />

          {showDetail ? (
            <>
              <ChoiceGroup
                legend="¿Cómo está el objeto? (opcional)"
                options={PHYSICAL_STATE_OPTIONS}
                value={physicalState}
                onChange={setPhysicalState}
              />
              <ChoiceGroup
                legend="¿Dónde estaba? (opcional)"
                options={BACKGROUND_OPTIONS}
                value={background}
                onChange={setBackground}
              />
              <label className="choice-group">
                <span className="muted">Nota (opcional). No se usa para entrenar.</span>
                <input
                  className="chip"
                  style={{ width: "100%" }}
                  value={note}
                  maxLength={MAX_NOTE_LENGTH}
                  placeholder="Ej.: vaso de café con tapa"
                  onChange={(event) => setNote(event.target.value)}
                />
              </label>
            </>
          ) : (
            <button type="button" className="button-ghost" onClick={() => setShowDetail(true)}>
              Añadir más detalle
            </button>
          )}
        </div>
      )}

      <div className="actions">
        <button
          type="button"
          className="button button-primary button-block"
          disabled={!complete}
          onClick={save}
        >
          Guardar aporte
        </button>
        <button type="button" className="button-ghost" onClick={onRetake}>
          Repetir la foto
        </button>
      </div>
    </div>
  );
}

function MaterialPicker({
  value,
  onChange,
  expanded,
  onExpand,
}: {
  value: Material | null;
  onChange: (material: Material) => void;
  expanded: boolean;
  onExpand: () => void;
}) {
  if (!expanded && value) {
    const info = MATERIAL_INFO[value];
    return (
      <div className="card">
        <h2>
          <span aria-hidden="true">{info.glyph} </span>
          {info.name}
        </h2>
        <button type="button" className="button-ghost" onClick={onExpand}>
          Cambiar material
        </button>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>¿Qué es esto?</h2>
      <div className="material-grid">
        {MATERIALS.map((material) => {
          const info = MATERIAL_INFO[material];
          return (
            <button
              key={material}
              type="button"
              className="material-tile"
              aria-pressed={value === material}
              onClick={() => onChange(material)}
            >
              <span className="glyph" aria-hidden="true">
                {info.glyph}
              </span>
              <span className="name">{info.name}</span>
              <span className="tiny">{info.examples}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
