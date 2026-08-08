/**
 * Perfil: nombre y, para quien es de la UMNG, clase, grupo y profesor.
 *
 * Los tres campos académicos existen por un motivo muy concreto: son lo que
 * permite a un profesor encontrar a sus estudiantes en el informe. Sin ellos el
 * aporte cuenta para el dataset pero no se le puede reconocer a nadie, que es
 * justo lo que Juan quiere poder hacer.
 *
 * **Se escriben con sugerencias de lo que ya escribieron otros.** Es el mismo
 * remedio que la lista cerrada de materiales, adaptado a un dominio que no
 * podemos enumerar: no conocemos el catálogo de asignaturas de la UMNG, pero sí
 * podemos evitar que el informe salga partido entre «Cálculo 1», «calculo I» y
 * «CALCULO 1». Se propone lo que ya existe; escribir algo nuevo sigue valiendo.
 */

import { useEffect, useState } from "react";
import { fetchAcademicSuggestions } from "../data/apiClient";
import {
  MAX_ACADEMIC_FIELD_LENGTH,
  MAX_NAME_LENGTH,
  normalizeProfile,
  profileProblemMessage,
  validateProfile,
  type Affiliation,
  type ProfileDraft,
} from "../domain/account";
import { Header, Notice } from "./components";

export function ProfileScreen({
  email,
  umngVerified,
  initial,
  saving,
  error,
  onSave,
  onSignOut,
  onSkip,
}: {
  email: string;
  /** `true` si entró con el correo institucional: entonces la adscripción no se pregunta, se sabe. */
  umngVerified: boolean;
  initial: ProfileDraft | null;
  saving: boolean;
  error: string | null;
  onSave: (draft: ProfileDraft) => void;
  onSignOut: () => void;
  onSkip: () => void;
}) {
  const [fullName, setFullName] = useState(initial?.fullName ?? "");
  const [affiliation, setAffiliation] = useState<Affiliation>(
    initial?.affiliation ?? (umngVerified ? "UMNG" : "GENERAL"),
  );
  const [course, setCourse] = useState(initial?.academic?.course ?? "");
  const [group, setGroup] = useState(initial?.academic?.group ?? "");
  const [professor, setProfessor] = useState(initial?.academic?.professor ?? "");
  const [touched, setTouched] = useState(false);

  const [courseOptions, setCourseOptions] = useState<string[]>([]);
  const [groupOptions, setGroupOptions] = useState<string[]>([]);
  const [professorOptions, setProfessorOptions] = useState<string[]>([]);

  useEffect(() => {
    if (affiliation !== "UMNG") return;
    void fetchAcademicSuggestions("course").then(setCourseOptions);
    void fetchAcademicSuggestions("group").then(setGroupOptions);
    void fetchAcademicSuggestions("professor").then(setProfessorOptions);
  }, [affiliation]);

  const draft: ProfileDraft = {
    fullName,
    affiliation,
    academic: affiliation === "UMNG" ? { course, group, professor } : null,
  };
  const problems = validateProfile(draft);

  return (
    <div className="screen">
      <Header
        right={
          <button type="button" className="button-ghost" onClick={onSignOut}>
            Salir
          </button>
        }
      />

      <h1>Tus datos</h1>
      <p className="muted">
        Entraste como <strong>{email}</strong>.
      </p>

      <div className="card">
        <label className="choice-group">
          <span className="muted">Nombre completo</span>
          <input
            className="chip"
            style={{ width: "100%" }}
            value={fullName}
            maxLength={MAX_NAME_LENGTH}
            autoComplete="name"
            placeholder="Como aparece en la lista de clase"
            onChange={(event) => setFullName(event.target.value)}
          />
        </label>

        {umngVerified ? (
          <Notice tone="info">
            Tu correo confirma que eres de la Universidad Militar Nueva Granada.
          </Notice>
        ) : (
          <fieldset className="choice-group" style={{ border: 0, padding: 0, margin: 0 }}>
            <legend className="muted" style={{ padding: 0, marginBottom: 6 }}>
              ¿Eres de la UMNG?
            </legend>
            <div className="choice-row">
              <button
                type="button"
                className="chip"
                aria-pressed={affiliation === "UMNG"}
                onClick={() => setAffiliation("UMNG")}
              >
                <span aria-hidden="true">🎓</span> Sí, de la UMNG
              </button>
              <button
                type="button"
                className="chip"
                aria-pressed={affiliation === "GENERAL"}
                onClick={() => setAffiliation("GENERAL")}
              >
                <span aria-hidden="true">🙋</span> Persona natural
              </button>
            </div>
          </fieldset>
        )}
      </div>

      {affiliation === "UMNG" && (
        <div className="card">
          <h2>Tu clase</h2>
          <p className="tiny">
            Esto es lo que permite a tu profesor encontrarte en el reporte de aportes.
          </p>
          <SuggestedField
            label="Clase o asignatura"
            value={course}
            options={courseOptions}
            placeholder="Ej.: Cálculo 1"
            onChange={setCourse}
          />
          <SuggestedField
            label="Grupo"
            value={group}
            options={groupOptions}
            placeholder="Ej.: B"
            onChange={setGroup}
          />
          <SuggestedField
            label="Profesor"
            value={professor}
            options={professorOptions}
            placeholder="Nombre y apellido"
            onChange={setProfessor}
          />
          {!umngVerified && (
            <Notice tone="warn">
              Como no entraste con el correo de la universidad, tu profesor verá que la pertenencia a
              la UMNG está declarada por ti y no comprobada.
            </Notice>
          )}
        </div>
      )}

      <Notice tone="info">
        Tu profesor verá tu nombre y cuántas fotos tuyas se aprobaron. Nada más.
      </Notice>

      {touched && problems.length > 0 && (
        <Notice tone="danger">
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {problems.map((problem) => (
              <li key={problem}>{profileProblemMessage(problem)}</li>
            ))}
          </ul>
        </Notice>
      )}

      {error && <Notice tone="danger">{error}</Notice>}

      <div className="actions">
        <button
          type="button"
          className="button button-primary button-block"
          disabled={saving}
          onClick={() => {
            setTouched(true);
            // Se envía normalizado. La API lo repite por su cuenta —es la
            // autoridad—, pero mandar «  calculo I » en crudo es ensuciar la
            // base a sabiendas.
            if (problems.length === 0) onSave(normalizeProfile(draft));
          }}
        >
          {saving ? "Guardando…" : "Guardar y empezar"}
        </button>
        <button type="button" className="button-ghost" onClick={onSkip}>
          Prefiero aportar sin dar mis datos
        </button>
      </div>
    </div>
  );
}

/**
 * Campo de texto con sugerencias. Usa `datalist`, que en móvil despliega las
 * opciones sin ocupar sitio y deja escribir cualquier cosa igualmente.
 */
function SuggestedField({
  label,
  value,
  options,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  options: readonly string[];
  placeholder: string;
  onChange: (value: string) => void;
}) {
  const listId = `sugerencias-${label.replace(/\s+/g, "-").toLowerCase()}`;
  return (
    <label className="choice-group">
      <span className="muted">{label}</span>
      <input
        className="chip"
        style={{ width: "100%" }}
        value={value}
        list={options.length > 0 ? listId : undefined}
        maxLength={MAX_ACADEMIC_FIELD_LENGTH}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
      {options.length > 0 && (
        <datalist id={listId}>
          {options.map((option) => (
            <option key={option} value={option} />
          ))}
        </datalist>
      )}
    </label>
  );
}
