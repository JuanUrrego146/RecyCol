#!/usr/bin/env bash
# Invariantes de arquitectura verificables por análisis estático (agente QA, issue #50).
# Complementa a :shared:verifyPlatformIsolation, que cubre el aislamiento de
# plataforma dentro de shared/. Aquí van las reglas que aplican al repo entero.
#
# Uso local:  bash .github/scripts/check_invariants.sh   (desde la raíz del repo)
set -u

fallos=0

reportar() {
  local titulo="$1"
  local coincidencias="$2"
  if [ -n "$coincidencias" ]; then
    echo "✗ ${titulo}"
    echo "$coincidencias" | sed 's/^/    /'
    fallos=$((fallos + 1))
  else
    echo "✓ ${titulo}"
  fi
}

buscar() {
  # grep devuelve 1 cuando no hay coincidencias: eso es el caso bueno, no un error.
  grep -rnE --include='*.kt' --exclude-dir=build "$@" 2>/dev/null || true
}

# Solo código de producción: los tests pueden comparar isoCode, etc.
solo_produccion() {
  grep -vE '/src/(test|androidTest)/|/(commonTest|jvmTest|androidUnitTest|androidInstrumentedTest)/' || true
}

echo "== Invariantes de arquitectura de BotaBien =="

# Convención del proyecto: nada de GlobalScope, ni en producción ni en pruebas.
reportar "Sin GlobalScope en ningún código Kotlin" \
  "$(buscar '\bGlobalScope\b' shared androidApp)"

# Invariante 3: lo específico de un país vive en el perfil JSON, nunca en código.
reportar "Sin comportamiento condicionado por país en código de producción" \
  "$(buscar '(country|isoCode)\s*[=!]=' shared androidApp | solo_produccion)"

# Invariante 7 / RNF-002: la ruta de clasificación no tiene dependencias de red.
# La v1 entera es offline; cualquier import de red en la app es sospechoso.
reportar "Sin dependencias de red en shared/ ni androidApp/" \
  "$(buscar '^\s*import\s+(java\.net\.|okhttp3?\.|retrofit2?\.|io\.ktor\.|com\.squareup\.okhttp)' shared androidApp)"

# Invariante 6 / RNF-012: los frames de cámara no se persisten.
reportar "Sin APIs de persistencia de imágenes en camera/ e inference/" \
  "$(buscar 'Bitmap\.compress|openFileOutput|FileOutputStream|MediaStore' androidApp/camera androidApp/inference)"

echo
if [ "$fallos" -gt 0 ]; then
  echo "Fallaron ${fallos} invariante(s). Revisa las coincidencias listadas."
  exit 1
fi
echo "Todos los invariantes verificables por análisis estático se cumplen."
