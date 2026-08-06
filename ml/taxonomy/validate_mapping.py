"""Validador del mapeo de taxonomía (S21 · RNF-016, RNF-017).

Comprueba que ``label_mapping.yaml`` cumple el criterio de hecho de S21:
toda etiqueta de origen tiene destino en la taxonomía o descarte justificado,
sin solapamientos ni destinos fuera del enum del dominio. Si el árbol de
trabajo contiene ``WasteMaterial.kt``, compara además la taxonomía declarada
con el enum real para detectar desincronización entre ``ml/`` y ``shared/``.

Uso::

    python taxonomy/validate_mapping.py

Sale con código 0 si el mapeo es válido; 1 si hay errores.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ML_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = ML_DIR.parent
MAPPING_PATH = Path(__file__).resolve().parent / "label_mapping.yaml"

KOTLIN_ENUM_ENTRY = re.compile(r"^\s*([A-Z][A-Z0-9_]*),\s*$")


def load_kotlin_taxonomy(source_rel: str) -> set[str] | None:
    """Extrae las constantes del enum Kotlin, o None si el archivo no está."""
    kt_path = REPO_ROOT / source_rel
    if not kt_path.is_file():
        return None
    materials: set[str] = set()
    in_enum = False
    for line in kt_path.read_text(encoding="utf-8").splitlines():
        if "enum class WasteMaterial" in line:
            in_enum = True
            continue
        if in_enum:
            if line.strip().startswith("}"):
                break
            match = KOTLIN_ENUM_ENTRY.match(line)
            if match:
                materials.add(match.group(1))
    return materials or None


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []

    mapping = yaml.safe_load(MAPPING_PATH.read_text(encoding="utf-8"))

    declared = mapping["target_taxonomy"]["materials"]
    taxonomy = set(declared)
    if len(declared) != len(taxonomy):
        errors.append("La taxonomía declarada contiene materiales duplicados.")

    kotlin_taxonomy = load_kotlin_taxonomy(mapping["target_taxonomy"]["source"])
    if kotlin_taxonomy is None:
        warnings.append(
            "WasteMaterial.kt no está en el árbol de trabajo: no se pudo "
            "verificar la sincronización con el enum del dominio."
        )
    elif kotlin_taxonomy != taxonomy:
        missing = kotlin_taxonomy - taxonomy
        extra = taxonomy - kotlin_taxonomy
        errors.append(
            "La taxonomía del YAML no coincide con WasteMaterial.kt "
            f"(faltan: {sorted(missing) or '—'}; sobran: {sorted(extra) or '—'})."
        )

    coverage: dict[str, set[str]] = {material: set() for material in taxonomy}

    for name, dataset in mapping["datasets"].items():
        labels: dict[str, str] = dataset.get("labels") or {}
        discards: dict[str, str] = dataset.get("discards") or {}

        if not labels and not discards:
            errors.append(f"[{name}] no declara ninguna etiqueta.")
        overlap = labels.keys() & discards.keys()
        if overlap:
            errors.append(f"[{name}] etiquetas a la vez mapeadas y descartadas: {sorted(overlap)}")

        for label, material in labels.items():
            if material not in taxonomy:
                errors.append(f"[{name}] '{label}' apunta a '{material}', que no está en la taxonomía.")
            else:
                coverage[material].add(name)

        for label, reason in discards.items():
            if not (isinstance(reason, str) and reason.strip()):
                errors.append(f"[{name}] el descarte de '{label}' no tiene justificación.")

    for material in sorted(taxonomy):
        if not coverage.get(material):
            warnings.append(
                f"'{material}' no recibe datos de ninguna fuente "
                "(brecha registrada en ml/DATASETS.md)."
            )

    print(f"Mapeo: {MAPPING_PATH.relative_to(REPO_ROOT)} (versión {mapping['version']})")
    print(f"Datasets: {len(mapping['datasets'])} · Materiales: {len(taxonomy)}")
    for material in sorted(taxonomy):
        sources = coverage.get(material) or {"—"}
        print(f"  {material:<16} ← {', '.join(sorted(sources))}")
    for warning in warnings:
        print(f"AVISO: {warning}")
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Mapeo válido: toda etiqueta tiene destino o descarte justificado.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
