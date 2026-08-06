# -*- coding: utf-8 -*-
"""Genera la vista Markdown del documento de requerimientos de BotaBien.

El documento oficial es el .docx que produce gen_doc.py. Este script genera,
a partir de la misma fuente de datos (gen_doc_data.py), una vista legible
directamente en GitHub, sin sustituir ni renombrar el .docx.

Uso:  py -3 tools/gen_doc_md.py
"""
import sys, os, io, re, unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_doc_data import (FECHA, RESPONSABLE, PROYECTO, EQUIPO, VERSIONES, DEFINICIONES,
                          JUSTIFICACION, DOC_RELACIONADA, FLUJO_PROCESO, CASOS_USO,
                          RF, RNF, OBSERVACIONES, APROBACIONES)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCX_NAME = "F_Analisis_de_Requerimientos_V1,0_BotaBien.docx"
OUT = os.path.join(ROOT, "docs", "F_Analisis_de_Requerimientos_V1,0_BotaBien.md")

CUS_IDS = [c[0] for c in CASOS_USO]

# ---------- trazabilidad (misma derivacion que gen_doc.py) ----------

MATRIX = ([(rid, set(cus)) for rid, _n, _d, _a, _rg, _io, _rel, cus in RF] +
          [(rid, set(cus)) for rid, _n, _d, _p, cus in RNF])


def reqs_for_cus(cus_id):
    rfs = [rid for rid, s in MATRIX if rid.startswith('RF-') and cus_id in s]
    rnfs = [rid for rid, s in MATRIX if rid.startswith('RNF-') and cus_id in s]
    return ", ".join(rfs) + (", " + ", ".join(rnfs) if rnfs else "")


# ---------- helpers de markdown ----------

def cell(text):
    """Escapa un valor para que no rompa una tabla Markdown."""
    return str(text).replace("|", "\\|").replace("\n", "<br>")


def slug(text):
    """Replica el anclado de encabezados de GitHub.

    GitHub elimina la puntuación y luego sustituye cada espacio por un guion,
    sin colapsar los espacios consecutivos: un guion largo entre dos espacios
    deja dos guiones en el ancla. Colapsarlos aquí generaría enlaces rotos.
    """
    s = text.strip().lower()
    s = re.sub(r"[^\w\s-]", "", s, flags=re.UNICODE)
    return re.sub(r"\s", "-", s)


OUTBUF = []


def w(line=""):
    OUTBUF.append(line)


def table(headers, rows, aligns=None):
    w("| " + " | ".join(cell(h) for h in headers) + " |")
    if aligns:
        w("|" + "|".join(aligns) + "|")
    else:
        w("|" + "|".join(["---"] * len(headers)) + "|")
    for r in rows:
        w("| " + " | ".join(cell(v) for v in r) + " |")
    w()


def fields_table(campos):
    """Tabla de dos columnas Campo/Valor, como las fichas del .docx."""
    table(["Campo", "Valor"], campos)


# ---------- portada ----------

w("# Análisis y Especificación de Requerimientos")
w()
w("## %s" % PROYECTO)
w()
w("*Clasificación de residuos por visión artificial en el dispositivo*")
w()
w("**Versión 1,0** · %s · %s" % (FECHA, RESPONSABLE))
w()
w("> [!NOTE]")
w("> Esta es la **vista en Markdown** del documento, legible directamente en GitHub.")
w("> El documento oficial con el formato de entrega es "
  "[`%s`](%s) — GitHub no puede previsualizar archivos de Word, "
  "así que ese enlace lo descarga." % (DOCX_NAME, DOCX_NAME.replace(",", "%2C").replace(" ", "%20")))
w("> Ambos se generan desde la misma fuente de datos (`tools/gen_doc_data.py`), de modo que no pueden divergir.")
w()
w("---")
w()

# ---------- tabla de contenido ----------

w("## Tabla de contenido")
w()
toc = [("1. Equipo del Proyecto", 0),
       ("2. Control de Versiones", 0),
       ("3. Definiciones, Siglas y Abreviaturas", 0),
       ("3.1 Justificación de la necesidad", 1),
       ("4. Documentación relacionada", 0),
       ("5. Diagrama flujo de actividades del proceso", 0),
       ("6. Casos de Uso", 0)]
for i, (cid, nombre, _a, _d, _f, _al) in enumerate(CASOS_USO, start=1):
    toc.append(("6.1.%d %s: %s" % (i, cid, nombre), 1))
toc += [("7. Requerimientos", 0),
        ("7.1 Requerimientos funcionales", 1),
        ("7.1.1 Lista de requerimientos funcionales", 2),
        ("7.1.2 Especificación de requerimientos funcionales", 2),
        ("7.2 Requerimientos No Funcionales", 1),
        ("8. Matriz de trazabilidad de Requerimientos vs Casos de uso", 0),
        ("9. Observaciones adicionales", 0),
        ("10. Control de revisión y aprobaciones del documento y sus anexos", 0)]
for titulo, nivel in toc:
    w("%s- [%s](#%s)" % ("  " * nivel, titulo, slug(titulo)))
w()
w("---")
w()

# ---------- 1. Equipo ----------

w("## 1. Equipo del Proyecto")
w()
table(["Rol", "Persona asignada"], EQUIPO)

# ---------- 2. Control de versiones ----------

w("## 2. Control de Versiones")
w()
table(["Fecha", "Versión", "Descripción", "Responsable de la versión"], VERSIONES)

# ---------- 3. Definiciones ----------

w("## 3. Definiciones, Siglas y Abreviaturas")
w()
for termino, definicion in DEFINICIONES:
    w("- **%s:** %s" % (termino, definicion))
w()

w("### 3.1 Justificación de la necesidad")
w()
for bloque in JUSTIFICACION.split("\n\n"):
    w(bloque)
    w()

# ---------- 4. Documentacion relacionada ----------

w("## 4. Documentación relacionada")
w()
table(["Título de documento", "Ubicación"], DOC_RELACIONADA)

# ---------- 5. Diagrama de flujo ----------

w("## 5. Diagrama flujo de actividades del proceso")
w()
w("El proceso principal del sistema, desde la apertura de la aplicación hasta la entrega de la "
  "recomendación de caneca, se describe en la siguiente secuencia de actividades. El diagrama de flujo "
  "correspondiente, junto con los diagramas de casos de uso, de clases, de secuencia y de estados, se "
  "encuentra en [`docs/arquitectura.md`](arquitectura.md) del repositorio, en notación Mermaid.")
w()
for i, paso in enumerate(FLUJO_PROCESO, start=1):
    w("%d. %s" % (i, paso))
w()
w("```mermaid")
w("flowchart TD")
for i, paso in enumerate(FLUJO_PROCESO, start=1):
    texto = paso.rstrip(".").replace('"', "'")
    w('    P%d["%d. %s"]' % (i, i, texto))
for i in range(1, len(FLUJO_PROCESO)):
    w("    P%d --> P%d" % (i, i + 1))
w("```")
w()

# ---------- 6. Casos de uso ----------

w("## 6. Casos de Uso")
w()
w("A continuación se especifican los casos de uso del sistema. Cada caso describe una interacción "
  "completa entre un actor y el sistema, con su flujo principal, sus flujos alternativos y los "
  "requerimientos que lo soportan.")
w()
for i, (cid, nombre, actor, desc, flujo, alternos) in enumerate(CASOS_USO, start=1):
    w("### 6.1.%d %s: %s" % (i, cid, nombre))
    w()
    w("- **Actor:** %s" % actor)
    w("- **Descripción:** %s" % desc)
    w()
    w("**Flujo principal:**")
    w()
    for j, paso in enumerate(flujo, start=1):
        w("%d. %s" % (j, paso))
    w()
    w("**Flujos alternativos:**")
    w()
    for alt in alternos:
        w("- %s" % alt)
    w()
    w("**Requerimientos asociados:** %s" % reqs_for_cus(cid))
    w()

# ---------- 7. Requerimientos ----------

w("---")
w()
w("## 7. Requerimientos")
w()
w("Un requerimiento describe una necesidad de negocio en términos de capacidades y/o servicios "
  "funcionales que ofrece un sistema, así como de las restricciones de calidad bajo las cuales debe "
  "operar. Los requerimientos funcionales definen qué debe hacer el sistema; los requerimientos no "
  "funcionales definen con qué nivel de calidad debe hacerlo.")
w()

w("### 7.1 Requerimientos funcionales")
w()
w("#### 7.1.1 Lista de requerimientos funcionales")
w()
table(["Identificador", "Nombre requerimiento"], [(x[0], x[1]) for x in RF])

w("#### 7.1.2 Especificación de requerimientos funcionales")
w()
for rid, nombre, desc, actor, reglas, interop, rel, cus in RF:
    w("##### %s · %s" % (rid, nombre))
    w()
    fields_table([
        ("Identificador", rid),
        ("Nombre del Requerimiento", nombre),
        ("Descripción", desc),
        ("Actor", actor),
        ("Reglas de negocio relacionadas", reglas),
        ("Interoperabilidad con otro sistema, módulo o componente", interop),
        ("Relaciones entre requerimientos", rel),
        ("Casos de uso relacionados / Historias de usuario relacionados", ", ".join(cus)),
        ("Responsable elaboración", RESPONSABLE),
        ("Fecha de elaboración", FECHA),
    ])

w("### 7.2 Requerimientos No Funcionales")
w()
table(["Identificador", "Nombre requerimiento"], [(x[0], x[1]) for x in RNF])

MARCAS = {"Alta":  "Alta ☒ · Media ☐ · Baja ☐",
          "Media": "Alta ☐ · Media ☒ · Baja ☐",
          "Baja":  "Alta ☐ · Media ☐ · Baja ☒"}

for rid, nombre, desc, prio, cus in RNF:
    w("#### %s · %s" % (rid, nombre))
    w()
    fields_table([
        ("Identificación del requerimiento", rid),
        ("Nombre del Requerimiento", nombre),
        ("Descripción", desc),
        ("Prioridad", MARCAS[prio]),
        ("Casos de uso relacionados", ", ".join(cus)),
        ("Responsable elaboración", RESPONSABLE),
        ("Fecha de elaboración", FECHA),
    ])

# ---------- 8. Matriz de trazabilidad ----------

w("---")
w()
w("## 8. Matriz de trazabilidad de Requerimientos vs Casos de uso")
w()
w("- **RF:** Requerimiento Funcional")
w("- **RNF:** Requerimiento No Funcional")
w("- **CUS:** Caso de Uso")
w()
aligns = [":---"] + [":---:"] * len(CUS_IDS)
rows = [[rid] + ["X" if cid in cusset else "" for cid in CUS_IDS] for rid, cusset in MATRIX]
table([""] + CUS_IDS, rows, aligns=aligns)

# ---------- 9. Observaciones ----------

w("## 9. Observaciones adicionales")
w()
for bloque in OBSERVACIONES.split("\n\n"):
    w(bloque)
    w()

# ---------- 10. Control de revision ----------

w("## 10. Control de revisión y aprobaciones del documento y sus anexos")
w()
table(["Rol", "Nombre", "Fecha", "Firma / Evidencia"],
      [(r, n, f, e if e else "—") for r, n, f, e in APROBACIONES])

w("---")
w()
w("<sub>Vista generada por `tools/gen_doc_md.py` desde `tools/gen_doc_data.py`. "
  "No editar a mano: los cambios se pierden al regenerar.</sub>")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
io.open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(OUTBUF) + "\n")

# ---------- verificacion ----------

print("Escrito: %s" % OUT)
print("Casos de uso: %d" % len(CASOS_USO))
print("RF: %d   RNF: %d   filas de matriz: %d" % (len(RF), len(RNF), len(MATRIX)))
vacios = [rid for rid, s in MATRIX if not s]
print("Filas de matriz vacías: %s" % (vacios if vacios else "ninguna"))
sin_rf = [c for c in CUS_IDS if not any(c in s for rid, s in MATRIX if rid.startswith('RF-'))]
print("CUS sin RF asociado: %s" % (sin_rf if sin_rf else "ninguno"))
