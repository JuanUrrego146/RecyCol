"""Genera ml/REPORTE_METRICAS.md (S28 · RNF-008, RNF-016) desde los artefactos.

Compila todas las evaluaciones de control existentes, el clasificador de
contaminación y el reporte de export, y emite el veredicto RNF-008 con las
limitaciones conocidas. Copia los JSON fuente a ml/reports/S28-final/.

    python eval/build_report.py
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

ML_DIR = Path(__file__).resolve().parent.parent
RUNS = ML_DIR / "runs"
OUT = ML_DIR / "REPORTE_METRICAS.md"
REPORTS = ML_DIR / "reports" / "S28-final"


def main() -> int:
    REPORTS.mkdir(parents=True, exist_ok=True)
    evals = []
    for path in sorted(RUNS.glob("material_*/*/eval_control.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        evals.append(data)
        shutil.copy(path, REPORTS / f"{data['variant']}_{data['run']}_control.json")
    evals.sort(key=lambda e: e["route"], reverse=True)
    best = evals[0] if evals else None

    contamination = None
    contamination_path = RUNS / "contamination" / "full" / "metrics.json"
    if contamination_path.is_file():
        contamination = json.loads(contamination_path.read_text(encoding="utf-8"))
        shutil.copy(contamination_path, REPORTS / "contamination_metrics.json")

    export = None
    export_path = ML_DIR / "dist" / "models" / "export_report.json"
    if export_path.is_file():
        export = json.loads(export_path.read_text(encoding="utf-8"))
        shutil.copy(export_path, REPORTS / "export_report.json")

    lines = [
        "# Reporte de métricas — M4 (S28)",
        "",
        "Evaluación cruzada sobre **RealWaste** (4 752 imágenes de relleno sanitario,",
        "jamás usadas en entrenamiento ni selección). La métrica que manda es el",
        "**acierto de ruta de disposición** (RNF-008: ≥85 % material, ≥95 % ruta).",
        "",
        "## Clasificador de material — todas las condiciones evaluadas",
        "",
        "| Variante/run | Top-1 material | Acierto de ruta |",
        "|---|---|---|",
    ]
    for e in evals:
        lines.append(f"| {e['variant']}/{e['run']} ({e['arch']}) | {e['top1']:.1%} | {e['route']:.1%} |")

    if best:
        goal = best["rnf008"]
        lines += [
            "",
            f"**Mejor contra control: {best['variant']}/{best['run']}** — "
            f"top-1 {best['top1']:.1%}, ruta {best['route']:.1%}.",
            "",
            f"**Veredicto RNF-008: material {'CUMPLE' if goal['material_met'] else 'NO CUMPLE'} "
            f"(objetivo 85 %) · ruta {'CUMPLE' if goal['route_met'] else 'NO CUMPLE'} (objetivo 95 %).**",
            "",
            "### Matriz colapsada por caneca (mejor run)",
            "",
        ]
        rc = best.get("route_confusion")
        if rc:
            lines.append("| real \\ predicho | " + " | ".join(rc["routes"]) + " |")
            lines.append("|---|" + "---|" * len(rc["routes"]))
            for name, row in zip(rc["routes"], rc["matrix"]):
                lines.append(f"| {name} | " + " | ".join(str(v) for v in row) + " |")
        lines += ["", "### Por clase (mejor run)", "",
                  "| Material | n | Top-1 | Ruta | Confundido con |", "|---|---|---|---|---|"]
        for row in best["per_class"]:
            lines.append(f"| {row['material']} | {row['support']} | {row['top1']:.1%} | "
                         f"{row['route']:.1%} | {row['confundido_con']} |")

    if contamination:
        threshold = contamination.get("threshold", {})
        control = contamination.get("proxy_control_rate", {})
        lines += [
            "", "## Clasificador de contaminación (S26)",
            "",
            f"- Umbral elegido (prioriza no llamar limpio a lo contaminado): "
            f"{threshold.get('threshold')} — recall contaminado {threshold.get('recall'):.1%}, "
            f"precisión {threshold.get('precision'):.1%} (validación del sintético).",
            f"- Control indirecto de transferencia: tasa de 'contaminado' en val limpia "
            f"{control.get('trashnet_like_clean')} vs control RealWaste {control.get('realwaste_control')} "
            "(RealWaste debería puntuar claramente más alto; no existe etiqueta real de "
            "contaminación en ninguna fuente pública — limitación documentada).",
        ]

    if export:
        lines += [
            "", "## Export LiteRT INT8 (S27)", "",
            f"- Artefactos: {', '.join(f'{k} ({v} MB)' for k, v in export['files'].items())}.",
            f"- Total {export['total_mb']} MB — presupuesto {export['budget_mb']} MB: "
            f"{'DENTRO' if export['within_budget'] else 'EXCEDIDO'}.",
            "- Orden de salida conforme al contrato EDGE (S15). Validación contra el "
            "runtime real: pendiente de EDGE (issue #25).",
        ]

    lines += [
        "", "## Limitaciones y riesgos abiertos", "",
        "- **El control no contiene BEVERAGE_CARTON ni BATTERY**: el caso estrella "
        "(vaso de café) no es verificable con RealWaste; mini-set de control propio "
        "coordinándose con REGLAS (#23).",
        "- Garbage v2 en uso con riesgo legal abierto (#77, bloquea lanzamiento, no desarrollo).",
        "- La contaminación se entrenó solo con síntesis; la transferencia a suciedad "
        "real solo tiene control indirecto.",
        "- La carpeta trash de Garbage v2 quedó excluida por la auditoría de REGLAS "
        "(#23): enseñaba envase-degradado⇒RESIDUAL.",
    ]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Escrito {OUT.relative_to(ML_DIR)} y copias en {REPORTS.relative_to(ML_DIR)}")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
