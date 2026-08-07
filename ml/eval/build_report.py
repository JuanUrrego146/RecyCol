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
    ]
    if best:
        lines += [
            "## Resumen",
            "",
            f"- **Modelo ganador: `{best['variant']}/{best['run']}` ({best['arch']})** — "
            f"contra control, top-1 **{best['top1']:.1%}** y ruta **{best['route']:.1%}**.",
            "- **RNF-008 no se cumple** (exige 85 % / 95 %). La brecha que queda es de "
            "**dominio**, no de arquitectura ni de cuantización: el mismo checkpoint pasa "
            "del 98 % de ruta en val interna al 74 % contra control.",
            "- **El hallazgo que desbloqueó M4**: la carpeta `trash` de Garbage v2 son "
            "envases sucios etiquetados como residual, y enseñaban «envase degradado ⇒ "
            "caneca negra». Excluirla subió la ruta de 61,4 % a ~70 % en la misma variante.",
            "- **La val interna no predice el control.** Ocurrió tres veces: con `full-v2` "
            "(mejor val, peor control), con EfficientNet-B2 (mejor val de todas, peor "
            "control que MobileNetV3-Large) y con la etapa 2 de contaminación (94 % en "
            "sintético, inútil en dominio real). Cualquier decisión tomada solo con val "
            "interna es sospechosa por defecto.",
            "- **La varianza entre dos ejecuciones idénticas es 2,16 pp de ruta.** "
            "Diferencias menores de ~2 pp entre runs no significan nada.",
            "",
        "## Clasificador de material — todas las condiciones evaluadas",
        "",
        "| Variante/run | Top-1 material | Acierto de ruta | Ruta macro (por clase) |",
        "|---|---|---|---|",
    ]
    for e in evals:
        # La ruta macro se añadió con la auditoría de REGLAS (#23): los evals
        # anteriores no la traen y se marcan con guion en vez de inventar un 0.
        macro = e.get("route_macro")
        macro_text = f"{macro:.1%}" if macro is not None else "—"
        lines.append(f"| {e['variant']}/{e['run']} ({e['arch']}) | {e['top1']:.1%} | "
                     f"{e['route']:.1%} | {macro_text} |")

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
            *([] if threshold.get("meets_min_recall", True) else
              [f"- ⚠️ **Ningún umbral alcanzó el recall mínimo exigido "
               f"({threshold.get('min_recall'):.0%})**: se eligió el de mayor recall. "
               "La etapa 2 no es fiable tal cual; aplica el plan B (preguntar al "
               "usuario) antes de cablearla."]),
            f"- Control indirecto de transferencia: tasa de 'contaminado' en val limpia "
            f"{control.get('trashnet_like_clean')} vs control RealWaste {control.get('realwaste_control')} "
            "(RealWaste debería puntuar claramente más alto; no existe etiqueta real de "
            "contaminación en ninguna fuente pública — limitación documentada).",
        ]
        # El umbral puede cumplir su recall en el val SINTETICO y aun asi no
        # transferir: es lo que paso en S26 (94 % en sintetico, 1,25 % de
        # RealWaste marcado). Sin este veredicto explicito, la tabla de arriba
        # se lee como un exito.
        degradado = control.get("realwaste_control")
        limpio = control.get("trashnet_like_clean")
        if degradado is not None and limpio is not None and degradado < 0.10:
            lines += [
                "",
                f"⚠️ **La etapa 2 no transfiere al dominio real.** Solo el "
                f"{degradado:.1%} de RealWaste — residuos degradados de relleno "
                f"sanitario — se marca como contaminado, frente al {limpio:.1%} de "
                "las fotos limpias de estudio. Un detector de suciedad que no ve "
                "suciedad en un relleno ha aprendido el artefacto de la síntesis, no "
                "la contaminación.",
                "",
                "**Decisión tomada (07/08): plan B activado.** La etapa 2 automática se "
                "sustituye por una pregunta al usuario, **solo para cartón y papel**, "
                "donde la contaminación es irreversible; plástico, vidrio y metal se "
                "enjuagan y no preguntan nada. El modelo queda entrenado y documentado "
                "pero **no es bloqueante para M4** y no se cablea en automático.",
                "",
                "Causa y palancas en `reports/S26-contaminacion/RESULTADO.md`. En corto: "
                "el fallo principal no es la mancha sino **el diseño del par** — limpio y "
                "sucio son la misma foto con y sin parche, así que la tarea se reduce a "
                "«¿hay algo superpuesto?». Acotado a fibra de celulosa el problema es "
                "bastante más fácil, porque la fibra **absorbe**: la grasa cambia "
                "translucidez y saturación del material en vez de superponer un parche "
                "opaco. Nada de esto es medible sin un conjunto real con etiqueta "
                "limpio/sucio — es la captura prioritaria de RecyCol Entrenamiento.",
            ]

    if export:
        lines += [
            "", "## Export LiteRT INT8 (S27)", "",
            f"- Artefactos: {', '.join(f'{k} ({v} MB)' for k, v in export['files'].items())}.",
            f"- Total {export['total_mb']} MB — presupuesto {export['budget_mb']} MB: "
            f"{'DENTRO' if export['within_budget'] else 'EXCEDIDO'}.",
        ]
        quant = export.get("quantization_loss") or {}
        if quant:
            lines += [
                "",
                "### Pérdida por cuantización INT8",
                "",
                "Medida con el intérprete de LiteRT sobre el **mismo split y el mismo "
                "run** que el checkpoint float, así que la resta aísla el efecto del "
                "INT8 y lo separa de la pérdida de dominio.",
                "",
                "| Variante | float top-1 | INT8 top-1 | Δ | float ruta | INT8 ruta | Δ |",
                "|---|---|---|---|---|---|---|",
            ]
            for variant, q in quant.items():
                lines.append(
                    f"| {variant} | {q['float_top1']:.1%} | {q['int8_top1']:.1%} | "
                    f"{q['delta_top1_pp']:+.1f} pp | {q['float_route']:.1%} | "
                    f"{q['int8_route']:.1%} | {q['delta_route_pp']:+.1f} pp |"
                )
            frag = [v for v, q in quant.items() if q["delta_top1_pp"] < -10]
            solido = [v for v, q in quant.items() if q["delta_route_pp"] > -3]
            if solido:
                lines += [
                    "",
                    f"**La brecha contra control no la causa el INT8.** {', '.join(solido)} "
                    "cuantiza sin pérdida apreciable de ruta, así que la distancia entre la "
                    "val interna y el control es de **dominio**, no de precisión numérica. "
                    "Era la pregunta que la separación float/INT8 venía a responder.",
                ]
            if frag:
                lines += [
                    "",
                    f"⚠️ **{', '.join(frag)} se degrada gravemente al cuantizar** (más de 10 pp "
                    "de top-1). Con el mismo pipeline de evaluación para las tres variantes, "
                    "que unas aguanten y otra no señala al modelo, no a la medición: "
                    "MobileNetV3-Small (hard-swish y bloques SE) es conocido por cuantizar "
                    "mal. **Afecta justo a la gama baja**, que es donde el modelo pequeño "
                    "hace falta. Alternativas: cuantización por canal más agresiva, "
                    "entrenamiento consciente de cuantización (QAT), o servir a la gama baja "
                    "el modelo de gama media si la latencia lo permite.",
                ]
        contrato = export.get("contrato_edge")
        if contrato and not contrato.get("cumple", True):
            lines += [
                "",
                "### ⚠️ Los artefactos NO cumplen el contrato de entrada de S15",
                "",
                f"- Exigido por el contrato: `{contrato['exigido']}`.",
                f"- Declarado por los artefactos: `{contrato['declarado_por_los_artefactos']}`.",
                f"- {contrato['nota']}",
                "",
                "**Los `.tflite` no se pueden cablear en la app tal cual.** El orden de "
                "salida sí es el del contrato; lo que no encaja es el layout y el tipo de "
                "entrada. Se detecta aquí, y no al integrar, porque "
                "`eval/evaluate_tflite.py` lee la firma real del artefacto en vez de "
                "asumirla.",
            ]
        lines += [
            "",
            "- Validación en dispositivo real (latencia y memoria por gama): pendiente, "
            "exige hardware Android — banco de EDGE (issue #25) y S41 de QA.",
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
        "- **RESIDUAL es la clase más débil** (2,8 % de top-1, 37,2 % de ruta): al retirar "
        "la carpeta `trash` se le quitaron 350 ejemplos. El error va en la dirección menos "
        "grave —residuo señalado como reciclable en vez de al revés— pero es deuda abierta.",
        "- **La gama alta llevaría hoy el peor modelo de los tres.** El contrato asigna "
        "EfficientNet-Lite2 a `high`, y EfficientNet-B2 rinde 4,4 pp por debajo de "
        "MobileNetV3-Large contra control pese a tener la mejor val. Cambiarlo toca el "
        "contrato congelado de S15: decisión de producto con issue de coordinación.",
        "- **Las palancas sensibles a coste de ruta empeoran** (−5,9 pp, 2,7 veces la "
        "varianza). Se descarta esa configuración, no la idea: optimizar la ruta en el "
        "dominio de entrenamiento, donde ya está al 98 %, solo rigidiza el modelo.",
        "- La reproducibilidad de la augmentación estaba rota (`hash()` de Python, "
        "aleatorizado por proceso). Corregido, pero **todo run anterior a esa corrección "
        "se midió sin saber la varianza**, incluido el barrido de arquitectura, cuyo "
        "ganador se decidió por 0,13 pp — muy por debajo del ruido.",
    ]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Escrito {OUT.relative_to(ML_DIR)} y copias en {REPORTS.relative_to(ML_DIR)}")
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
