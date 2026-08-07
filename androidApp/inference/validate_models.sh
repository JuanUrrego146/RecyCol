#!/usr/bin/env bash
#
# Validación autónoma de exports LiteRT (S27, issue #25) — la corre ML sin EDGE.
#
# Uso:
#   ./androidApp/inference/validate_models.sh [DIR_MODELOS] [DIR_EVAL]
#
#   DIR_MODELOS  carpeta con los .tflite a validar (por defecto ml/dist/models)
#   DIR_EVAL     opcional: carpeta de evaluación con labels.csv, imágenes y
#                reference/ (se copia a src/androidTest/assets/eval/)
#
# Requisitos: Android SDK con `adb` en el PATH y un dispositivo/emulador
# conectado (en Windows, ejecutar desde Git Bash). En emulador solo se mide
# la vía CPU; la latencia por gama real exige dispositivos físicos.
#
# Qué hace:
#   1. Copia los .tflite a los assets del módulo e imprime su SHA-256
#      (comparar con el export_report.json de ML).
#   2. Copia el conjunto de evaluación si se indicó.
#   3. Corre el banco instrumentado completo: contrato, lote de referencia,
#      exactitud INT8 vs float y latencia/memoria.
#   4. Extrae el registro legible (líneas REGISTRO-*) de logcat y lo guarda
#      en androidApp/inference/build/registro-validacion.txt.
#
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$MODULE_DIR/../.." && pwd)"
MODELS_SRC="${1:-$ROOT_DIR/ml/dist/models}"
EVAL_SRC="${2:-}"
ASSETS_DIR="$MODULE_DIR/src/main/assets/models"
REPORT="$MODULE_DIR/build/registro-validacion.txt"

echo "== Banco de validación de exports LiteRT (S27) =="

command -v adb >/dev/null 2>&1 || {
    echo "ERROR: no se encontró adb. Instalar Android SDK platform-tools y añadirlo al PATH." >&2
    exit 1
}
adb get-state >/dev/null 2>&1 || {
    echo "ERROR: no hay dispositivo/emulador conectado (adb get-state falló)." >&2
    exit 1
}

shopt -s nullglob
models=("$MODELS_SRC"/*.tflite)
[ ${#models[@]} -gt 0 ] || {
    echo "ERROR: no hay .tflite en $MODELS_SRC. Nombres esperados: ver README (contrato de modelos)." >&2
    exit 1
}

mkdir -p "$ASSETS_DIR"
echo
echo "== Modelos y hashes SHA-256 (comparar con export_report.json) =="
for model in "${models[@]}"; do
    cp "$model" "$ASSETS_DIR/"
    sha256sum "$ASSETS_DIR/$(basename "$model")"
done

if [ -n "$EVAL_SRC" ]; then
    mkdir -p "$MODULE_DIR/src/androidTest/assets/eval"
    cp -r "$EVAL_SRC"/. "$MODULE_DIR/src/androidTest/assets/eval/"
    echo
    echo "== Conjunto de evaluación copiado desde $EVAL_SRC =="
fi

echo
echo "== Corriendo el banco instrumentado (contrato + referencia + INT8 + latencia) =="
adb logcat -c || true
TESTS_OK=1
(cd "$ROOT_DIR" && ./gradlew :androidApp:inference:connectedDebugAndroidTest --console=plain) || TESTS_OK=0

mkdir -p "$MODULE_DIR/build"
# System.out de la instrumentación va a logcat: de ahí sale el registro.
adb logcat -d -s System.out:I 2>/dev/null | grep -E "REGISTRO-(S27|S20)" | sed 's/^.*REGISTRO-/REGISTRO-/' > "$REPORT" || true

echo
echo "== REGISTRO DE VALIDACIÓN (guardado en $REPORT) =="
if [ -s "$REPORT" ]; then
    cat "$REPORT"
else
    echo "(sin líneas de registro: revisar si las pruebas se omitieron por falta de assets)"
fi

echo
if [ "$TESTS_OK" -eq 1 ]; then
    echo "RESULTADO: banco en verde. Si hay gemelos float, la pérdida de cuantización está arriba."
else
    echo "RESULTADO: HAY FALLOS — el detalle está en el reporte HTML de Gradle:" >&2
    echo "  androidApp/inference/build/reports/androidTests/connected/" >&2
    echo "Un fallo de contrato o de lote de referencia significa export fuera de contrato: reportar en #25." >&2
    exit 1
fi
