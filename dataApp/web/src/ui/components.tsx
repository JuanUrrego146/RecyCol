/** Piezas reutilizables de la interfaz. Nada de lógica de negocio aquí. */

import type { ReactNode } from "react";

export function ProgressBar({ ratio, label }: { ratio: number; label?: string }) {
  const percent = Math.round(Math.min(1, Math.max(0, ratio)) * 100);
  return (
    <div>
      <div
        className="progress"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-label={label}
      >
        <span style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}

export interface Option<T> {
  readonly value: T;
  readonly name: string;
  readonly glyph: string;
}

export function ChoiceGroup<T extends string>({
  legend,
  options,
  value,
  onChange,
}: {
  legend: string;
  options: readonly Option<T>[];
  value: T | null;
  onChange: (value: T) => void;
}) {
  return (
    <fieldset className="choice-group" style={{ border: 0, padding: 0, margin: 0 }}>
      <legend className="muted" style={{ padding: 0, marginBottom: 6 }}>
        {legend}
      </legend>
      <div className="choice-row">
        {options.map((option) => (
          <button
            key={option.value}
            type="button"
            className="chip"
            aria-pressed={value === option.value}
            onClick={() => onChange(option.value)}
          >
            <span aria-hidden="true">{option.glyph}</span>
            {option.name}
          </button>
        ))}
      </div>
    </fieldset>
  );
}

export function Notice({
  tone = "info",
  children,
}: {
  tone?: "info" | "warn" | "danger";
  children: ReactNode;
}) {
  return <div className={`notice notice-${tone}`}>{children}</div>;
}

export function Header({ right }: { right?: ReactNode }) {
  return (
    <header className="header">
      <div className="brand">
        Recy<span>Col</span> Aporta
      </div>
      {right}
    </header>
  );
}
