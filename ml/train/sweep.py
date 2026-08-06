"""Barrido de hiperparámetros del clasificador de material (S25 · RNF-008).

Ejecuta una parrilla de configuraciones de forma secuencial (cada run en un
subproceso, con su `metrics.json` propio) y escribe un resumen comparativo en
``runs/sweep_summary.md``. La selección final se hace con datos: mejor acierto
de ruta en validación, desempatando por top-1 — y se contrasta después contra
el control (S28), nunca al revés.

Parrilla por defecto:

- Piloto de lr sobre MobileNetV3-Small (la variante más barata).
- Comparación de arquitecturas con el mejor lr del piloto: MobileNetV3-Small,
  MobileNetV3-Large y EfficientNet-B2 (el plan citaba Lite0/Lite2; torchvision
  no publica pesos de esas variantes exactas — queda registrado aquí y la
  decisión se toma comparando lo disponible, no por intuición).
- Una ablación sin augmentación para medir cuánto aporta S23.

    python train/sweep.py            # parrilla completa
    python train/sweep.py --dry-run  # lista los runs sin ejecutar
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

ML_DIR = Path(__file__).resolve().parent.parent
RUNS = ML_DIR / "runs"

PILOT_LRS = [5e-5, 1e-4, 3e-4]
ARCH_VARIANTS = ["low", "mid", "high"]


def run_config(variant: str, run_name: str, extra: list[str]) -> dict | None:
    metrics_path = RUNS / f"material_{variant}" / run_name / "metrics.json"
    if metrics_path.is_file():
        print(f"[sweep] {variant}/{run_name}: ya existe, se reutiliza")
        return json.loads(metrics_path.read_text(encoding="utf-8"))
    command = [sys.executable, str(ML_DIR / "train" / "train_material.py"),
               "--variant", variant, "--run-name", run_name, *extra]
    print(f"[sweep] {variant}/{run_name}: {' '.join(command[2:])}")
    result = subprocess.run(command, cwd=ML_DIR)
    if result.returncode != 0:
        print(f"[sweep] {variant}/{run_name} FALLÓ (código {result.returncode})", file=sys.stderr)
        return None
    return json.loads(metrics_path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--phase2-epochs", type=int, default=12)
    args = parser.parse_args()

    plan: list[tuple[str, str, list[str]]] = []
    for lr in PILOT_LRS:
        plan.append(("low", f"lr{lr:g}",
                     ["--lr-phase2", str(lr), "--phase2-epochs", str(args.phase2_epochs)]))
    # Los runs de arquitectura usan el lr ganador del piloto; se añaden tras él.

    if args.dry_run:
        for variant, name, extra in plan:
            print(f"{variant}/{name}: {' '.join(extra)}")
        return 0

    results: dict[str, dict] = {}
    for variant, name, extra in plan:
        metrics = run_config(variant, name, extra)
        if metrics:
            results[f"{variant}/{name}"] = metrics

    pilot = {k: v for k, v in results.items() if k.startswith("low/lr")}
    if not pilot:
        print("Ningún run del piloto terminó — no hay ganador de lr.", file=sys.stderr)
        return 1
    best_key = max(pilot, key=lambda k: pilot[k]["best_val_route"])
    best_lr = pilot[best_key]["lr_phase2"]
    print(f"[sweep] lr ganador del piloto: {best_lr:g} ({best_key})")

    for variant in ARCH_VARIANTS:
        name = f"arch-lr{best_lr:g}"
        metrics = run_config(variant, name,
                             ["--lr-phase2", str(best_lr),
                              "--phase2-epochs", str(args.phase2_epochs)])
        if metrics:
            results[f"{variant}/{name}"] = metrics

    metrics = run_config("low", "no-augment",
                         ["--lr-phase2", str(best_lr), "--no-augment",
                          "--phase2-epochs", str(args.phase2_epochs)])
    if metrics:
        results["low/no-augment"] = metrics

    lines = ["# Resumen del barrido S25", "",
             "| Run | Arch | lr fase 2 | Mejor ruta (val) | Top-1 último | s/época |",
             "|---|---|---|---|---|---|"]
    for key in sorted(results):
        m = results[key]
        last = m["history"][-1] if m["history"] else {}
        lines.append(f"| {key} | {m['arch']} | {m['lr_phase2']:g} | "
                     f"{m['best_val_route']:.4f} | {last.get('val_top1', '—')} | "
                     f"{last.get('seconds', '—')} |")
    lines += ["", "La métrica que manda es el acierto de ruta (RNF-008); la "
              "evaluación definitiva es sobre el control en S28."]
    (RUNS / "sweep_summary.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"Resumen: {(RUNS / 'sweep_summary.md').relative_to(ML_DIR)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
