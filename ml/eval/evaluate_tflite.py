"""Evaluación del ``.tflite`` INT8 sobre el mismo split que el checkpoint float (S27).

Separa las dos pérdidas que hoy están mezcladas en la brecha 91 % → 42 %:

- **pérdida de cuantización** — este script contra ``eval/evaluate_control.py``
  sobre el *mismo* split y el *mismo* run: la única diferencia es INT8 vs float;
- **pérdida de dominio** — ``evaluate_control.py`` en val contra control.

El banco de EDGE (``androidApp/inference/validate_models.sh``) mide lo mismo
*más* latencia y memoria, pero exige un dispositivo o emulador Android conectado
(``connectedDebugAndroidTest``) y en esta máquina no hay ninguno. La parte que no
depende de hardware — la exactitud que se pierde al cuantizar — se mide aquí, con
el intérprete de LiteRT, y el resultado es directamente comparable porque
reutiliza las funciones de métricas de ``train_material``.

El preprocesado **no se asume**: se lee de la firma real del tensor de entrada
del artefacto y se contrasta con el contrato EDGE↔ML de S15 (``[1, lado, lado,
3]`` UINT8, RGB por filas). Un artefacto que no cumpla se evalúa igual, pero el
reporte lo marca — descubrir eso al cablear la app sería mucho más caro.

    python eval/evaluate_tflite.py --model dist/models/material_low.tflite
    python eval/evaluate_tflite.py --model dist/models/material_low.tflite --split val
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ML_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_DIR))

from eval.evaluate_control import per_class_table, route_confusion  # noqa: E402
from train.train_material import (  # noqa: E402
    MANIFESTS, MATERIALS, MATERIAL_INDEX, macro_route, route_by_material,
)

IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


def input_layout(detail: dict) -> dict:
    """Interpreta la firma del tensor de entrada y la contrasta con el contrato."""
    shape = [int(v) for v in detail["shape"]]
    dtype = np.dtype(detail["dtype"]).name
    # NHWC si el último eje son 3 canales; NCHW si lo son el segundo.
    if len(shape) == 4 and shape[3] == 3:
        layout, side = "NHWC", shape[1]
    elif len(shape) == 4 and shape[1] == 3:
        layout, side = "NCHW", shape[2]
    else:
        raise SystemExit(f"Forma de entrada no reconocida: {shape}")
    quantization = detail.get("quantization", (0.0, 0))
    scale, zero_point = float(quantization[0]), int(quantization[1])
    contract_ok = layout == "NHWC" and dtype == "uint8"
    return {"shape": shape, "dtype": dtype, "layout": layout, "side": side,
            "scale": scale, "zero_point": zero_point,
            "cumple_contrato_edge": contract_ok,
            "nota_contrato": (
                "conforme al contrato S15" if contract_ok else
                f"el contrato S15 exige [1,lado,lado,3] UINT8 y el artefacto declara "
                f"{shape} {dtype} ({layout}): EDGE debe declararlo en su ModelSpec "
                f"o hay que reexportar")}


def preprocess(path: Path, layout: dict) -> np.ndarray:
    with Image.open(path) as img:
        image = img.convert("RGB").resize((layout["side"], layout["side"]), Image.BILINEAR)
    if layout["dtype"] == "uint8":
        # Entrada cuantizada: el modelo lleva la normalización dentro y recibe
        # los bytes RGB tal cual, que es lo que le llega desde la cámara.
        array = np.asarray(image, dtype=np.uint8)
    else:
        array = np.asarray(image, dtype=np.float32) / 255.0
        array = ((array - IMAGENET_MEAN) / IMAGENET_STD).astype(np.float32)
    if layout["layout"] == "NCHW":
        array = np.transpose(array, (2, 0, 1))
    return array[np.newaxis, ...]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, help="ruta al .tflite (relativa a ml/)")
    parser.add_argument("--split", default="control", choices=["control", "val"])
    parser.add_argument("--limit", type=int, default=None,
                        help="evalúa solo las primeras N filas (diagnóstico rápido)")
    args = parser.parse_args()

    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError as error:
        raise SystemExit(f"Falta ai_edge_litert en el contenedor: {error}")

    model_path = ML_DIR / args.model
    if not model_path.is_file():
        print(f"Falta {args.model}: ejecuta antes export/export_litert.py", file=sys.stderr)
        return 1

    interpreter = Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    in_detail = interpreter.get_input_details()[0]
    out_detail = interpreter.get_output_details()[0]
    layout = input_layout(in_detail)
    print(json.dumps({"entrada": layout, "salida": {
        "shape": [int(v) for v in out_detail["shape"]],
        "dtype": np.dtype(out_detail["dtype"]).name}}, indent=2, ensure_ascii=False))

    n_classes = int(out_detail["shape"][-1])
    if n_classes != len(MATERIALS):
        # Misma regla que el clasificador de EDGE: fallar explícito, nunca mapear
        # en silencio (contrato S15, punto 3).
        print(f"El artefacto emite {n_classes} clases y la taxonomía tiene "
              f"{len(MATERIALS)}: contrato roto.", file=sys.stderr)
        return 2

    with (MANIFESTS / f"{args.split}.csv").open(encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    if args.limit:
        rows = rows[:args.limit]

    routes = route_by_material()
    confusion = np.zeros((len(MATERIALS), len(MATERIALS)), dtype=np.int64)
    out_scale, out_zero = out_detail.get("quantization", (0.0, 0))
    for index, row in enumerate(rows):
        interpreter.set_tensor(in_detail["index"],
                               preprocess(ML_DIR / "data" / row["path"], layout))
        interpreter.invoke()
        logits = interpreter.get_tensor(out_detail["index"])[0].astype(np.float32)
        if out_scale:
            logits = (logits - out_zero) * out_scale
        confusion[MATERIAL_INDEX[row["material"]], int(np.argmax(logits))] += 1
        if index % 500 == 0:
            print(f"  {index}/{len(rows)}", flush=True)

    total = int(confusion.sum())
    top1 = float(np.trace(confusion) / max(total, 1))
    route_hits = sum(int(confusion[t, p])
                     for t in range(len(MATERIALS)) for p in range(len(MATERIALS))
                     if routes.get(MATERIALS[t]) == routes.get(MATERIALS[p]))
    report = {
        "model": args.model, "runtime": "ai_edge_litert", "split": args.split,
        "n": total, "top1": round(top1, 4),
        "route": round(route_hits / max(total, 1), 4),
        "route_macro": round(macro_route(confusion, routes), 4),
        "entrada": layout,
        "per_class": per_class_table(confusion, routes),
        "route_confusion": route_confusion(confusion, routes),
    }
    out_path = model_path.parent / f"eval_{model_path.stem}_{args.split}.json"
    out_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items()
                      if k not in ("per_class", "route_confusion")},
                     indent=2, ensure_ascii=False))
    print(f"Guardado en {out_path.relative_to(ML_DIR)}")
    print("Pérdida de cuantización = este resultado menos el del checkpoint float "
          f"en el mismo split ({args.split}): eval/evaluate_control.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
