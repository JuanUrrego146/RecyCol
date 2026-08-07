"""Entrenamiento del clasificador de material por variante de gama (S25).

Transfer learning en dos fases sobre los manifiestos de S22:

- Fase 1: backbone congelado, solo la cabeza (lr alto, pocas épocas).
- Fase 2: red completa con lr bajo.

La cabeza emite 11 logits **en el orden de declaración de ``WasteMaterial``**,
que es el contrato de salida fijado por el agente EDGE en S15
(``androidApp/inference/README.md``). ``ELECTRONIC`` no tiene datos en v1 por
decisión de producto: su logit existe y simplemente nunca gana.

Métricas por época: top-1 de material y acierto de ruta de disposición, con la
ruta derivada del perfil normativo activo (``shared/resources/profiles/co.json``)
— la métrica que manda es la ruta (RNF-008).

Uso (dentro del contenedor de ML)::

    python train/train_material.py --variant low            # MobileNetV3-Small 224
    python train/train_material.py --variant mid            # MobileNetV3-Large 224
    python train/train_material.py --variant high           # EfficientNet-B2 260
    python train/train_material.py --variant low --smoke    # 1+1 épocas, subset
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from pathlib import Path

import numpy as np
import torch
import torchvision
from PIL import Image
from torch import nn
from torch.utils.data import DataLoader, Dataset

ML_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_DIR))

from augment.mobile_domain import augment_image, rng_for  # noqa: E402

MANIFESTS = ML_DIR / "data" / "manifests"
RUNS = ML_DIR / "runs"
PROFILE = ML_DIR.parent / "shared" / "resources" / "profiles" / "co.json"

# Contrato EDGE↔ML (S15): orden de declaración de WasteMaterial.kt.
MATERIALS = ["PLASTIC", "PAPER", "CARDBOARD", "BEVERAGE_CARTON", "GLASS", "METAL",
             "ORGANIC", "TEXTILE", "BATTERY", "ELECTRONIC", "RESIDUAL"]
MATERIAL_INDEX = {m: i for i, m in enumerate(MATERIALS)}

VARIANTS = {
    # tier -> (constructor torchvision, pesos, lado de entrada)
    # Nota registrada: el plan citaba MobileNetV3-Large 0.75 y EfficientNet-Lite2;
    # torchvision no publica pesos preentrenados de esas variantes exactas, así
    # que v1 usa Large 1.0 y EfficientNet-B2 (mismos tamaños de entrada del
    # contrato). Si la latencia de S27 lo exige, se revisa con timm.
    "low": ("mobilenet_v3_small", 224),
    "mid": ("mobilenet_v3_large", 224),
    "high": ("efficientnet_b2", 260),
}

TORCH_SEED = 20260806


def route_by_material() -> dict[str, str]:
    """material → ruta de disposición según el perfil normativo activo."""
    profile = json.loads(PROFILE.read_text(encoding="utf-8"))
    bin_route = {b["id"]: b["route"] for b in profile["bins"]}
    return {r["material"]: bin_route[r["targetBin"]] for r in profile["rules"]}


class ManifestDataset(Dataset):
    def __init__(self, manifest: Path, input_side: int, train: bool, epoch: int = 0,
                 limit: int | None = None, use_augment: bool = True,
                 exclude: set[tuple[str, str]] | None = None):
        with manifest.open(encoding="utf-8") as handle:
            self.rows = list(csv.DictReader(handle))
        if exclude:
            # Exclusión declarativa por (dataset, material): el manifiesto de S22
            # no se toca — la reproducibilidad de la partición se conserva y cada
            # run deja constancia de lo que quitó en su metrics.json.
            before = len(self.rows)
            self.rows = [r for r in self.rows
                         if (r["dataset"], r["material"]) not in exclude]
            print(f"exclude {sorted(exclude)}: {before} -> {len(self.rows)} filas")
        if limit:
            self.rows = self.rows[:limit]
        self.input_side = input_side
        self.train = train
        self.epoch = epoch
        self.use_augment = use_augment

    def __len__(self) -> int:
        return len(self.rows)

    def __getitem__(self, index: int):
        row = self.rows[index]
        with Image.open(ML_DIR / "data" / row["path"]) as img:
            image = img.convert("RGB")
        if self.train and self.use_augment:
            # Augmentación de dominio móvil (S23), reproducible por imagen+época.
            image = augment_image(image, rng_for(f"{row['path']}#e{self.epoch}"))
        image = image.resize((self.input_side, self.input_side), Image.BILINEAR)
        tensor = torch.from_numpy(np.asarray(image, dtype=np.float32) / 255.0)
        tensor = tensor.permute(2, 0, 1)
        mean = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
        std = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
        return (tensor - mean) / std, MATERIAL_INDEX[row["material"]]


def build_model(arch: str) -> nn.Module:
    weights = "DEFAULT"  # pesos ImageNet de torchvision (BSD-3; ml/DATA_LICENSES.md)
    model = getattr(torchvision.models, arch)(weights=weights)
    if arch.startswith("mobilenet"):
        in_features = model.classifier[-1].in_features
        model.classifier[-1] = nn.Linear(in_features, len(MATERIALS))
    else:  # efficientnet
        in_features = model.classifier[-1].in_features
        model.classifier[-1] = nn.Linear(in_features, len(MATERIALS))
    return model


def route_cost_matrix(routes: dict[str, str]) -> torch.Tensor:
    """Matriz [C,C] de coste por confusión: ~0 dentro de la misma ruta de
    disposición, 1.0 al cruzar de caneca (auditoría de REGLAS en #23: el error
    intra-blanca es gratis para el usuario; el cruce blanca↔negra/verde es el
    error caro que gobierna RNF-008)."""
    cost = torch.ones(len(MATERIALS), len(MATERIALS))
    for i, a in enumerate(MATERIALS):
        for j, b in enumerate(MATERIALS):
            if routes.get(a) == routes.get(b):
                cost[i, j] = 0.0
    return cost


def class_weights(rows: list[dict]) -> torch.Tensor:
    counts = np.zeros(len(MATERIALS))
    for row in rows:
        counts[MATERIAL_INDEX[row["material"]]] += 1
    weights = np.where(counts > 0, counts.sum() / np.maximum(counts, 1), 0.0)
    weights = weights / max(weights.max(), 1e-9)
    return torch.tensor(weights, dtype=torch.float32)


def evaluate(model: nn.Module, loader: DataLoader, routes: dict[str, str],
             device: str = "cpu"):
    model.eval()
    confusion = np.zeros((len(MATERIALS), len(MATERIALS)), dtype=np.int64)
    with torch.no_grad():
        for batch, labels in loader:
            predictions = model(batch.to(device, non_blocking=True)).argmax(dim=1).cpu()
            for true, pred in zip(labels.tolist(), predictions.tolist()):
                confusion[true, pred] += 1
    total = confusion.sum()
    top1 = np.trace(confusion) / max(total, 1)
    route_hits = sum(
        confusion[t, p]
        for t in range(len(MATERIALS))
        for p in range(len(MATERIALS))
        if routes.get(MATERIALS[t]) == routes.get(MATERIALS[p])
    )
    return top1, route_hits / max(total, 1), confusion


def macro_route(confusion: np.ndarray, routes: dict[str, str]) -> float:
    """Acierto de ruta promediado **por clase**, no por imagen.

    La val interna está dominada por TEXTILE (586) y ORGANIC (472): con el
    micro-promedio esas dos deciden el checkpoint y una clase minoritaria puede
    hundirse sin que la métrica se entere. El macro da el mismo peso a cada
    material, que es lo que exige la auditoría de #23 — acierto de caneca por
    clase. El control (RealWaste) sigue sin intervenir en ninguna selección.
    """
    per_class = []
    for t in range(len(MATERIALS)):
        support = int(confusion[t].sum())
        if support == 0:
            continue
        hits = sum(int(confusion[t, p]) for p in range(len(MATERIALS))
                   if routes.get(MATERIALS[p]) == routes.get(MATERIALS[t]))
        per_class.append(hits / support)
    return float(np.mean(per_class)) if per_class else 0.0


def run_epoch(model, loader, optimizer, criterion, device, scaler=None,
              route_cost: torch.Tensor | None = None, cost_weight: float = 0.0) -> float:
    model.train()
    total_loss = 0.0

    def compute_loss(logits, labels):
        loss = criterion(logits, labels)
        if route_cost is not None and cost_weight > 0.0:
            # Coste esperado de cruzar de caneca: E[p·C[y]] — penaliza la masa
            # de probabilidad puesta en materiales de otra ruta.
            probabilities = torch.softmax(logits.float(), dim=1)
            expected = (probabilities * route_cost[labels]).sum(dim=1).mean()
            loss = loss + cost_weight * expected
        return loss

    for batch, labels in loader:
        batch = batch.to(device, non_blocking=True)
        labels = labels.to(device)
        optimizer.zero_grad()
        if scaler is not None:
            # Precisión mixta (AMP): en Ampere acelera y reduce VRAM, clave
            # para los 8 GB de la 3060 Ti.
            with torch.autocast(device_type="cuda", dtype=torch.float16):
                loss = compute_loss(model(batch), labels)
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
        else:
            loss = compute_loss(model(batch), labels)
            loss.backward()
            optimizer.step()
        total_loss += loss.item() * len(labels)
    return total_loss / max(len(loader.dataset), 1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=VARIANTS, required=True)
    parser.add_argument("--phase1-epochs", type=int, default=3)
    parser.add_argument("--phase2-epochs", type=int, default=12)
    parser.add_argument("--lr-phase1", type=float, default=1e-3)
    parser.add_argument("--lr-phase2", type=float, default=1e-4)
    parser.add_argument("--run-name", default=None,
                        help="subdirectorio de runs/ (por defecto full/smoke)")
    parser.add_argument("--no-augment", action="store_true",
                        help="ablación: entrena sin la augmentación de S23")
    parser.add_argument("--require-gpu", action="store_true",
                        help="aborta si CUDA no está disponible: evita el fallo silencioso a CPU")
    parser.add_argument("--route-cost", type=float, default=0.0,
                        help="peso de la pérdida sensible a coste de ruta (0 = solo cross-entropy)")
    parser.add_argument("--select", choices=["route", "route-macro"], default="route",
                        help="criterio de checkpoint: ruta micro (histórico) o macro por clase")
    parser.add_argument("--exclude", action="append", default=[], metavar="DATASET:MATERIAL",
                        help="descarta filas de train y val (repetible); p. ej. "
                             "garbage_dataset_v2:RESIDUAL — la carpeta trash de v2 que la "
                             "auditoría de REGLAS (#23) señaló como envases sucios mal etiquetados")
    parser.add_argument("--batch-size", type=int, default=None,
                        help="por defecto 64 en GPU, 32 en CPU")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--smoke", action="store_true",
                        help="1+1 épocas sobre un subset: valida el circuito, no entrena de verdad")
    args = parser.parse_args()

    torch.manual_seed(TORCH_SEED)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    if args.require_gpu and device != "cuda":
        print("--require-gpu: CUDA no disponible, se aborta para no entrenar en CPU por accidente.",
              file=sys.stderr)
        return 2
    if args.batch_size is None:
        # La variante high (EfficientNet-B2 a 260 px) no cabe con batch 64 en
        # los ~5,6 GiB efectivos de la 3060 Ti (el escritorio retiene ~2,3).
        args.batch_size = (32 if args.variant == "high" else 64) if device == "cuda" else 32
    pin = device == "cuda"
    print(f"device={device}" + (f" ({torch.cuda.get_device_name(0)})" if pin else ""))
    arch, side = VARIANTS[args.variant]
    routes = route_by_material()
    limit = 512 if args.smoke else None
    phase1 = 1 if args.smoke else args.phase1_epochs
    phase2 = 1 if args.smoke else args.phase2_epochs

    exclude = set()
    for spec in args.exclude:
        dataset_name, _, material = spec.partition(":")
        if not material:
            print(f"--exclude '{spec}': se espera DATASET:MATERIAL", file=sys.stderr)
            return 2
        exclude.add((dataset_name, material))

    train_ds = ManifestDataset(MANIFESTS / "train.csv", side, train=True, limit=limit,
                               use_augment=not args.no_augment, exclude=exclude)
    val_ds = ManifestDataset(MANIFESTS / "val.csv", side, train=False, limit=limit,
                             exclude=exclude)
    print(f"variant={args.variant} arch={arch} input={side} "
          f"train={len(train_ds)} val={len(val_ds)}")

    val_loader = DataLoader(val_ds, batch_size=args.batch_size, num_workers=args.workers,
                            pin_memory=pin)
    criterion = nn.CrossEntropyLoss(weight=class_weights(train_ds.rows).to(device))
    model = build_model(arch).to(device)
    scaler = torch.amp.GradScaler("cuda") if device == "cuda" else None
    cost_matrix = route_cost_matrix(routes).to(device) if args.route_cost > 0 else None

    run_name = args.run_name or ("smoke" if args.smoke else "full")
    run_dir = RUNS / f"material_{args.variant}" / run_name
    run_dir.mkdir(parents=True, exist_ok=True)
    history = []
    best_route = 0.0
    best_score = 0.0

    def train_phase(name: str, epochs: int, lr: float, full_net: bool):
        nonlocal best_route, best_score
        for param in model.parameters():
            param.requires_grad = full_net
        if not full_net:
            for param in model.classifier.parameters():
                param.requires_grad = True
        optimizer = torch.optim.AdamW(
            (p for p in model.parameters() if p.requires_grad), lr=lr, weight_decay=1e-4
        )
        for epoch in range(epochs):
            train_ds.epoch = hash((name, epoch)) & 0xFFFF  # varía la augmentación
            loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True,
                                num_workers=args.workers, pin_memory=pin,
                                generator=torch.Generator().manual_seed(TORCH_SEED + epoch))
            start = time.time()
            loss = run_epoch(model, loader, optimizer, criterion, device, scaler,
                             route_cost=cost_matrix, cost_weight=args.route_cost)
            top1, route_acc, confusion = evaluate(model, val_loader, routes, device)
            route_macro = macro_route(confusion, routes)
            score = route_macro if args.select == "route-macro" else route_acc
            vram_mb = (round(torch.cuda.max_memory_allocated() / 1e6)
                       if device == "cuda" else 0)
            history.append({"phase": name, "epoch": epoch, "loss": round(loss, 4),
                            "val_top1": round(float(top1), 4),
                            "val_route": round(float(route_acc), 4),
                            "val_route_macro": round(route_macro, 4),
                            "seconds": round(time.time() - start, 1),
                            "vram_peak_mb": vram_mb})
            print(f"[{name} e{epoch}] loss={loss:.4f} top1={top1:.3f} "
                  f"ruta={route_acc:.3f} ruta_macro={route_macro:.3f} "
                  f"({history[-1]['seconds']}s, VRAM pico {vram_mb} MB)")
            if score > best_score:
                best_score = score
                best_route = route_acc
                torch.save(model.state_dict(), run_dir / "best.pt")
                np.savetxt(run_dir / "confusion_val.csv", confusion, fmt="%d",
                           delimiter=",", header=",".join(MATERIALS))

    train_phase("phase1_head", phase1, lr=args.lr_phase1, full_net=False)
    train_phase("phase2_full", phase2, lr=args.lr_phase2, full_net=True)

    (run_dir / "metrics.json").write_text(
        json.dumps({"variant": args.variant, "arch": arch, "input": side,
                    "lr_phase1": args.lr_phase1, "lr_phase2": args.lr_phase2,
                    "batch_size": args.batch_size, "route_cost": args.route_cost,
                    "select": args.select, "exclude": sorted(args.exclude),
                    "train_rows": len(train_ds), "val_rows": len(val_ds),
                    "materials": MATERIALS, "history": history,
                    "best_val_route": best_route,
                    "best_val_score": best_score}, indent=2),
        encoding="utf-8")
    print(f"Resultados en {run_dir.relative_to(ML_DIR)} · criterio {args.select} "
          f"= {best_score:.3f} (ruta micro {best_route:.3f})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
