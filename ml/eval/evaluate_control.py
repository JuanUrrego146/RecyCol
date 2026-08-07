"""Evaluación cruzada sobre el conjunto de control (S28 · RNF-008, RNF-016).

Evalúa un checkpoint del clasificador de material sobre ``control.csv``
(RealWaste, jamás visto en entrenamiento ni en ajuste) y reporta lo que exige
RNF-008: top-1 de material, acierto de ruta de disposición y matriz de
confusión por clase. La regla del proyecto es dura: ninguna métrica medida
sobre los datasets de entrenamiento cuenta como evidencia de generalización.

    python eval/evaluate_control.py --variant low --run full
    python eval/evaluate_control.py --variant low --run full --split val   # referencia interna
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import DataLoader

ML_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_DIR))

from train.train_material import (  # noqa: E402
    MANIFESTS, MATERIALS, RUNS, VARIANTS, ManifestDataset, build_model,
    evaluate, route_by_material,
)


def per_class_table(confusion: np.ndarray, routes: dict[str, str]) -> list[dict]:
    rows = []
    for index, material in enumerate(MATERIALS):
        support = int(confusion[index].sum())
        if support == 0:
            continue
        correct = int(confusion[index, index])
        route_ok = sum(int(confusion[index, p]) for p in range(len(MATERIALS))
                       if routes.get(MATERIALS[p]) == routes.get(material))
        top_confusion = int(np.argsort(confusion[index])[-1])
        second = int(np.argsort(confusion[index])[-2])
        main_error = MATERIALS[second] if top_confusion == index else MATERIALS[top_confusion]
        rows.append({"material": material, "support": support,
                     "top1": round(correct / support, 4),
                     "route": round(route_ok / support, 4),
                     "confundido_con": main_error})
    return rows


def route_confusion(confusion: np.ndarray, routes: dict[str, str]) -> dict:
    """Matriz colapsada por ruta de disposición: lo que el usuario vive
    (auditoría de REGLAS en #23 — se publica siempre junto a la de material)."""
    names = sorted({routes.get(m, "?") for m in MATERIALS})
    index = {name: i for i, name in enumerate(names)}
    collapsed = np.zeros((len(names), len(names)), dtype=np.int64)
    for t in range(len(MATERIALS)):
        for p in range(len(MATERIALS)):
            collapsed[index[routes.get(MATERIALS[t], "?")],
                      index[routes.get(MATERIALS[p], "?")]] += confusion[t, p]
    return {"routes": names, "matrix": collapsed.tolist()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=VARIANTS, required=True)
    parser.add_argument("--run", default="full")
    parser.add_argument("--split", default="control", choices=["control", "val"])
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--workers", type=int, default=4)
    args = parser.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    arch, side = VARIANTS[args.variant]
    checkpoint = RUNS / f"material_{args.variant}" / args.run / "best.pt"
    if not checkpoint.is_file():
        print(f"Falta {checkpoint.relative_to(ML_DIR)}", file=sys.stderr)
        return 1

    model = build_model(arch)
    model.load_state_dict(torch.load(checkpoint, map_location="cpu", weights_only=True))
    model = model.to(device)

    dataset = ManifestDataset(MANIFESTS / f"{args.split}.csv", side, train=False)
    loader = DataLoader(dataset, batch_size=args.batch_size, num_workers=args.workers,
                        pin_memory=device == "cuda")
    routes = route_by_material()
    top1, route_acc, confusion = evaluate(model, loader, routes, device)

    report = {
        "variant": args.variant, "run": args.run, "arch": arch,
        "split": args.split, "n": int(confusion.sum()),
        "top1": round(float(top1), 4), "route": round(float(route_acc), 4),
        "rnf008": {"material_goal": 0.85, "route_goal": 0.95,
                   "material_met": bool(top1 >= 0.85), "route_met": bool(route_acc >= 0.95)},
        "per_class": per_class_table(confusion, routes),
        "route_confusion": route_confusion(confusion, routes),
    }
    out_dir = RUNS / f"material_{args.variant}" / args.run
    out_path = out_dir / f"eval_{args.split}.json"
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    np.savetxt(out_dir / f"confusion_{args.split}.csv", confusion, fmt="%d",
               delimiter=",", header=",".join(MATERIALS))

    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"Guardado en {out_path.relative_to(ML_DIR)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
