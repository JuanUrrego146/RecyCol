"""Entrenamiento del clasificador binario de contaminación (S26 · RF-021).

Entrena sobre los pares limpio/contaminado sintetizados en S24 y ajusta el
umbral de decisión con prioridad en NO clasificar como limpio algo contaminado
(un falso "limpio" manda un reciclable sucio a la caneca blanca: es el error
caro). Salida de 2 logits en el orden ``[CLEAN, CONTAMINATED]`` fijado por el
contrato EDGE de S15.

Evaluación de transferencia a suciedad real: no existe etiqueta limpio/sucio
en ningún dataset del inventario, así que se usa un control indirecto —
TrashNet (fotos de estudio, mayormente limpias) debería puntuar bajo en
contaminación y RealWaste (residuos reales de relleno sanitario) claramente
más alto. Es un proxy, no una métrica exacta; queda documentado como
limitación en el reporte de S28.

Uso::

    python train/train_contamination.py            # entrenamiento completo
    python train/train_contamination.py --smoke    # circuito rápido
"""

from __future__ import annotations

import argparse
import csv
import hashlib
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

PAIRS = ML_DIR / "data" / "derived" / "contamination" / "pairs.csv"
MANIFESTS = ML_DIR / "data" / "manifests"
RUNS = ML_DIR / "runs"
CLASSES = ["CLEAN", "CONTAMINATED"]   # contrato EDGE↔ML (S15): este orden
INPUT_SIDE = 224
TORCH_SEED = 20260806
VAL_PERCENT = 15


def val_bucket(key: str) -> bool:
    digest = hashlib.md5(f"{key}|s26-val".encode("utf-8")).hexdigest()
    return int(digest, 16) % 100 < VAL_PERCENT


class PairDataset(Dataset):
    """Cada par (limpio, sucio) aporta dos muestras con etiqueta opuesta."""

    def __init__(self, split: str, train: bool, epoch: int = 0, limit: int | None = None):
        if not PAIRS.is_file():
            raise SystemExit("Falta data/derived/contamination/pairs.csv: ejecuta antes contaminate/synthesize.py (S24).")
        with PAIRS.open(encoding="utf-8") as handle:
            pairs = list(csv.DictReader(handle))
        samples = []
        for pair in pairs:
            bucket = "val" if val_bucket(pair["clean"]) else "train"
            if bucket != split:
                continue
            samples.append((pair["clean"], 0))
            samples.append((pair["dirty"], 1))
        if limit:
            samples = samples[:limit]
        self.samples = samples
        self.train = train
        self.epoch = epoch

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, index: int):
        path, label = self.samples[index]
        with Image.open(ML_DIR / "data" / path) as img:
            image = img.convert("RGB")
        if self.train:
            image = augment_image(image, rng_for(f"{path}#c{self.epoch}"))
        image = image.resize((INPUT_SIDE, INPUT_SIDE), Image.BILINEAR)
        tensor = torch.from_numpy(np.asarray(image, dtype=np.float32) / 255.0).permute(2, 0, 1)
        mean = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
        std = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
        return (tensor - mean) / std, label


def contamination_scores(model: nn.Module, loader: DataLoader,
                         device: str = "cpu") -> tuple[np.ndarray, np.ndarray]:
    model.eval()
    scores, labels = [], []
    with torch.no_grad():
        for batch, batch_labels in loader:
            logits = model(batch.to(device, non_blocking=True))
            probabilities = torch.softmax(logits, dim=1)[:, 1].cpu()
            scores.extend(probabilities.tolist())
            labels.extend(batch_labels.tolist())
    return np.asarray(scores), np.asarray(labels)


def pick_threshold(scores: np.ndarray, labels: np.ndarray, min_recall: float = 0.92):
    """Umbral más exigente que aún detecta ≥ min_recall de los contaminados."""
    best = {"threshold": 0.5, "recall": 0.0, "precision": 0.0}
    for threshold in np.arange(0.05, 0.95, 0.01):
        predicted = scores >= threshold
        true_positive = int(np.sum(predicted & (labels == 1)))
        recall = true_positive / max(int(np.sum(labels == 1)), 1)
        precision = true_positive / max(int(np.sum(predicted)), 1)
        if recall >= min_recall and threshold > best["threshold"] or best["recall"] < min_recall:
            best = {"threshold": round(float(threshold), 2), "recall": round(recall, 4),
                    "precision": round(precision, 4)}
    return best


def proxy_control(model: nn.Module, manifest: Path, limit: int, threshold: float) -> float:
    """Tasa de 'contaminado' que el modelo asigna a un manifiesto externo."""
    with manifest.open(encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))[:limit]

    class Rows(Dataset):
        def __len__(self):
            return len(rows)

        def __getitem__(self, index):
            with Image.open(ML_DIR / "data" / rows[index]["path"]) as img:
                image = img.convert("RGB").resize((INPUT_SIDE, INPUT_SIDE), Image.BILINEAR)
            tensor = torch.from_numpy(np.asarray(image, dtype=np.float32) / 255.0).permute(2, 0, 1)
            mean = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
            std = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
            return (tensor - mean) / std, 0

    device = next(model.parameters()).device.type
    scores, _ = contamination_scores(model, DataLoader(Rows(), batch_size=32), device)
    return float(np.mean(scores >= threshold))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--smoke", action="store_true")
    args = parser.parse_args()

    torch.manual_seed(TORCH_SEED)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    pin = device == "cuda"
    limit = 256 if args.smoke else None
    epochs = 1 if args.smoke else args.epochs

    train_ds = PairDataset("train", train=True, limit=limit)
    val_ds = PairDataset("val", train=False, limit=limit)
    print(f"contaminación: train={len(train_ds)} val={len(val_ds)} device={device}")
    val_loader = DataLoader(val_ds, batch_size=args.batch_size, num_workers=args.workers,
                            pin_memory=pin)

    model = torchvision.models.mobilenet_v3_small(weights="DEFAULT")
    model.classifier[-1] = nn.Linear(model.classifier[-1].in_features, len(CLASSES))
    model = model.to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.AdamW(model.parameters(), lr=2e-4, weight_decay=1e-4)
    scaler = torch.amp.GradScaler("cuda") if device == "cuda" else None

    run_dir = RUNS / "contamination" / ("smoke" if args.smoke else "full")
    run_dir.mkdir(parents=True, exist_ok=True)
    history = []
    for epoch in range(epochs):
        train_ds.epoch = epoch
        loader = DataLoader(train_ds, batch_size=args.batch_size, shuffle=True,
                            num_workers=args.workers, pin_memory=pin,
                            generator=torch.Generator().manual_seed(TORCH_SEED + epoch))
        model.train()
        start, total_loss = time.time(), 0.0
        for batch, labels in loader:
            batch, labels = batch.to(device, non_blocking=True), labels.to(device)
            optimizer.zero_grad()
            if scaler is not None:
                with torch.autocast(device_type="cuda", dtype=torch.float16):
                    loss = criterion(model(batch), labels)
                scaler.scale(loss).backward()
                scaler.step(optimizer)
                scaler.update()
            else:
                loss = criterion(model(batch), labels)
                loss.backward()
                optimizer.step()
            total_loss += loss.item() * len(labels)
        scores, labels = contamination_scores(model, val_loader, device)
        accuracy = float(np.mean((scores >= 0.5) == labels))
        history.append({"epoch": epoch, "loss": round(total_loss / max(len(train_ds), 1), 4),
                        "val_acc@0.5": round(accuracy, 4),
                        "seconds": round(time.time() - start, 1)})
        print(f"[e{epoch}] loss={history[-1]['loss']} acc@0.5={accuracy:.3f}")

    scores, labels = contamination_scores(model, val_loader, device)
    threshold = pick_threshold(scores, labels)
    control = {}
    for name, manifest in (("trashnet_like_clean", MANIFESTS / "val.csv"),
                           ("realwaste_control", MANIFESTS / "control.csv")):
        if manifest.is_file():
            control[name] = round(proxy_control(model, manifest, limit=400,
                                                threshold=threshold["threshold"]), 4)

    torch.save(model.state_dict(), run_dir / "best.pt")
    (run_dir / "metrics.json").write_text(json.dumps({
        "classes": CLASSES, "threshold": threshold, "history": history,
        "proxy_control_rate": control,
        "note": ("proxy_control_rate es la fracción clasificada como CONTAMINATED en conjuntos "
                 "externos sin etiqueta real de contaminación: control indirecto, ver S28."),
    }, indent=2), encoding="utf-8")
    print(f"umbral={threshold} · control indirecto={control} · resultados en {run_dir.relative_to(ML_DIR)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
