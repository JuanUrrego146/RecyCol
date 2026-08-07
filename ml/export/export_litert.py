"""Cuantización INT8 y exportación a LiteRT por gama (S27 · RNF-001, RNF-006).

Convierte los checkpoints de S25/S26 a los cuatro artefactos del contrato
EDGE↔ML de S15 (``androidApp/inference/README.md``):

===================  ==========================  =======
Archivo              Modelo                       Entrada
===================  ==========================  =======
material_low.tflite  MobileNetV3-Small            224 px
material_mid.tflite  MobileNetV3-Large            224 px
material_high.tflite EfficientNet-B2              260 px
contamination.tflite MobileNetV3-Small (binario)  224 px
===================  ==========================  =======

Cuantización posterior al entrenamiento *full-integer* (activaciones y pesos
INT8, entrada UINT8 RGB como exige el contrato) calibrada con muestras
representativas del manifiesto de entrenamiento. Los artefactos quedan en
``ml/dist/models/``; empaquetarlos en la app es ámbito del agente EDGE.

Uso::

    python export/export_litert.py --variant low
    python export/export_litert.py --all
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np
import torch
from PIL import Image

ML_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_DIR))

from train.train_material import MATERIALS, VARIANTS, build_model  # noqa: E402

MANIFESTS = ML_DIR / "data" / "manifests"
RUNS = ML_DIR / "runs"
DIST = ML_DIR / "dist" / "models"
CALIBRATION_SAMPLES = 200
SIZE_BUDGET_MB = 150

CONTRACT_NAMES = {"low": "material_low.tflite", "mid": "material_mid.tflite",
                  "high": "material_high.tflite", "contamination": "contamination.tflite"}


def calibration_batches(input_side: int):
    """Muestras representativas del train para calibrar la cuantización."""
    with (MANIFESTS / "train.csv").open(encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    step = max(len(rows) // CALIBRATION_SAMPLES, 1)
    for row in rows[::step][:CALIBRATION_SAMPLES]:
        with Image.open(ML_DIR / "data" / row["path"]) as img:
            image = img.convert("RGB").resize((input_side, input_side), Image.BILINEAR)
        array = np.asarray(image, dtype=np.float32) / 255.0
        array = (array - [0.485, 0.456, 0.406]) / [0.229, 0.224, 0.225]
        yield torch.from_numpy(array.astype(np.float32)).permute(2, 0, 1).unsqueeze(0)


def load_trained(kind: str, run: str = "no-trash") -> tuple[torch.nn.Module, int]:
    if kind == "contamination":
        import torchvision
        from torch import nn

        model = torchvision.models.mobilenet_v3_small()
        model.classifier[-1] = nn.Linear(model.classifier[-1].in_features, 2)
        checkpoint = RUNS / "contamination" / "full" / "best.pt"
        side = 224
    else:
        arch, side = VARIANTS[kind]
        model = build_model(arch)
        checkpoint = RUNS / f"material_{kind}" / run / "best.pt"
    if not checkpoint.is_file():
        raise SystemExit(f"Falta {checkpoint.relative_to(ML_DIR)}: entrena antes esa variante (S25/S26).")
    model.load_state_dict(torch.load(checkpoint, map_location="cpu", weights_only=True))
    model.eval()
    return model, side


def export_variant(kind: str, run: str = "no-trash") -> Path:
    # ai-edge-torch fue renombrado a litert-torch. El paquete viejo instalable
    # hoy (0.7.2) es un shim vacío, y las versiones anteriores que sí traían
    # .quantize fijan un tf-nightly concreto que PyPI ya purgó: son
    # ininstalables. Se prefiere litert-torch (API de estructura idéntica) y se
    # conserva el nombre antiguo como respaldo por si la imagen es vieja.
    try:
        try:
            import litert_torch as edge_torch
            from litert_torch.quantize import pt2e_quantizer, quant_config
        except ImportError:
            import ai_edge_torch as edge_torch
            from ai_edge_torch.quantize import pt2e_quantizer, quant_config
        # La API PT2E se migró de torch.ao a torchao a partir de torch 2.9.
        try:
            from torchao.quantization.pt2e.quantize_pt2e import convert_pt2e, prepare_pt2e
        except ImportError:
            from torch.ao.quantization.quantize_pt2e import convert_pt2e, prepare_pt2e
    except ImportError as error:
        raise SystemExit(
            "Falta el conversor a LiteRT en el contenedor. litert-torch exige "
            "torch>=2.11 y la imagen de entrenamiento lleva 2.6: instala ambos en el "
            "contenedor de export (ver ml/reports/S27-export/RESULTADO.md). "
            f"Detalle: {error}"
        )

    model, side = load_trained(kind, run)
    sample = (torch.zeros(1, 3, side, side),)

    quantizer = pt2e_quantizer.PT2EQuantizer().set_global(
        pt2e_quantizer.get_symmetric_quantization_config(is_per_channel=True)
    )
    # torch.export.export_for_training se consolidó en torch.export.export a
    # partir de torch 2.12; se prueban las dos por si la imagen es vieja.
    export_for_training = getattr(torch.export, "export_for_training", torch.export.export)
    captured = export_for_training(model, sample).module()
    prepared = prepare_pt2e(captured, quantizer)
    for batch in calibration_batches(side):
        prepared(batch)
    quantized = convert_pt2e(prepared, fold_quantize=False)

    edge_model = edge_torch.convert(
        quantized, sample,
        quant_config=quant_config.QuantConfig(pt2e_quantizer=quantizer),
    )
    DIST.mkdir(parents=True, exist_ok=True)
    out = DIST / CONTRACT_NAMES[kind]
    edge_model.export(str(out))
    print(f"{out.relative_to(ML_DIR)}: {out.stat().st_size / 1e6:.1f} MB")
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=list(CONTRACT_NAMES))
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--run", default="no-trash",
                        help="run de material a exportar. La receta ganadora de S25 es "
                             "no-trash (--exclude garbage_dataset_v2:RESIDUAL); para la "
                             "variante low el mejor checkpoint es el de no-trash-seed")
    args = parser.parse_args()
    kinds = list(CONTRACT_NAMES) if args.all else [args.variant]
    if not kinds or kinds == [None]:
        parser.error("indica --variant o --all")

    exported = [export_variant(kind, args.run) for kind in kinds]
    total_mb = sum(path.stat().st_size for path in exported) / 1e6
    report = {
        "files": {path.name: round(path.stat().st_size / 1e6, 2) for path in exported},
        "total_mb": round(total_mb, 2),
        "budget_mb": SIZE_BUDGET_MB,
        "within_budget": total_mb <= SIZE_BUDGET_MB,
        "output_order": {"material": MATERIALS, "contamination": ["CLEAN", "CONTAMINATED"]},
    }
    (DIST / "export_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    if not report["within_budget"]:
        print("ERROR: los artefactos exceden el presupuesto de tamaño", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
