/**
 * Informe para los profesores y sugerencias de campos académicos.
 *
 * `GET /api/report/academic` — cuánto aportó cada estudiante. Solo administración.
 * `GET /api/academic/suggestions` — lo que ya escribieron otros, para no repetir
 *   el problema de las etiquetas de material a menor escala.
 *
 * ## Por qué el informe cuenta lo que cuenta
 *
 * Dar puntos por fotos crea el incentivo de inflar el número, así que el informe
 * **no cuenta envíos**. Cuenta:
 *
 * - **Fotos aprobadas**: lo que pasó la moderación. Enviar basura no suma.
 * - **Objetos distintos**: treinta fotos de la misma lata son un objeto. Es lo
 *   que separa a quien recorrió el campus de quien vació el bolsillo delante de
 *   la cámara.
 * - **Imágenes distintas**: cuántos pHash distintos hay. Ver más abajo.
 * - **Materiales distintos**: premia la variedad, que es justo lo que el modelo
 *   necesita y lo que a un estudiante le cuesta más falsificar.
 *
 * El profesor recibe las columnas y decide. Nosotros no inventamos una nota.
 *
 * ## Por qué dos columnas para lo mismo
 *
 * `objetos_distintos` cuenta `object_id`, y **ese identificador lo genera el
 * navegador**: nada impide mandar uno nuevo por foto y convertir treinta fotos
 * de la misma lata en treinta objetos. Como de esa columna puede depender una
 * nota, al lado va `imagenes_distintas`, que cuenta pHash distintos — y dos fotos
 * con pHash distinto son dos fotos de verdad distintas, no la misma reenviada.
 *
 * Ninguna de las dos es a prueba de todo: el pHash también lo calcula el
 * navegador, así que quien programe un cliente propio puede inventarse los dos
 * números. Lo que separan es a quien infla el conteo con la aplicación de verdad
 * —que es el caso realista— de quien recorrió el campus. **Contra un cliente
 * falsificado lo que hay es la moderación**, que es la única barrera que mira las
 * imágenes de una en una.
 *
 * Cuando las dos columnas se separan mucho, la de abajo es la de fiar: muchos
 * objetos y pocas imágenes distintas es la firma de la misma foto reenviada.
 *
 * ## Verificado contra declarado
 *
 * La columna `verificado` dice si la pertenencia a la UMNG viene del correo
 * institucional o de haberla escrito a mano. No es lo mismo y el profesor tiene
 * que poder distinguirlo antes de poner una nota.
 */

import { app, HttpRequest, HttpResponseInit } from "@azure/functions";
import { isAdministrator, readPrincipal } from "../auth";
import { toCsv } from "../csv";
import { canonicalKey, type ContributorDocument } from "../model";
import { eachApproved, ensureTables, listAccountContributors } from "../store";

interface CaptureFact {
  contributorId: string;
  objectId: string;
  phash: string;
  material: string;
  capturedAt: string;
}

interface ReportRow {
  nombre: string;
  correo: string;
  adscripcion: string;
  verificado: string;
  clase: string;
  grupo: string;
  profesor: string;
  fotos_aprobadas: number;
  objetos_distintos: number;
  imagenes_distintas: number;
  materiales_distintos: number;
  primer_aporte: string;
  ultimo_aporte: string;
}

const COLUMNS: readonly (keyof ReportRow)[] = [
  "nombre",
  "correo",
  "adscripcion",
  "verificado",
  "clase",
  "grupo",
  "profesor",
  "fotos_aprobadas",
  "objetos_distintos",
  "imagenes_distintas",
  "materiales_distintos",
  "primer_aporte",
  "ultimo_aporte",
];

export async function academicReport(request: HttpRequest): Promise<HttpResponseInit> {
  if (!isAdministrator(request)) {
    return { status: 403, jsonBody: { message: "Solo administración" } };
  }

  const professorFilter = request.query.get("professor");
  const groupFilter = request.query.get("group");
  const courseFilter = request.query.get("course");
  const format = request.query.get("format") === "csv" ? "csv" : "json";

  await ensureTables();
  const [contributors, facts] = await Promise.all([listAccountContributors(), approvedFacts()]);

  // Las capturas de los identificadores anónimos enlazados cuentan para su
  // persona: alguien que aportó antes de entrar en su cuenta no debe perderlas.
  const ownerOf = new Map<string, ContributorDocument>();
  for (const contributor of contributors) {
    ownerOf.set(contributor.id, contributor);
    for (const linked of contributor.linkedContributorIds ?? []) {
      ownerOf.set(linked, contributor);
    }
  }

  const aggregates = new Map<
    string,
    {
      photos: number;
      objects: Set<string>;
      images: Set<string>;
      materials: Set<string>;
      first: string;
      last: string;
    }
  >();

  for (const fact of facts) {
    const owner = ownerOf.get(fact.contributorId);
    if (!owner) continue; // aportante anónimo: no sale en el informe, por diseño
    const current = aggregates.get(owner.id) ?? {
      photos: 0,
      objects: new Set<string>(),
      images: new Set<string>(),
      materials: new Set<string>(),
      first: fact.capturedAt,
      last: fact.capturedAt,
    };
    current.photos += 1;
    current.objects.add(fact.objectId);
    current.images.add(fact.phash);
    current.materials.add(fact.material);
    if (fact.capturedAt < current.first) current.first = fact.capturedAt;
    if (fact.capturedAt > current.last) current.last = fact.capturedAt;
    aggregates.set(owner.id, current);
  }

  const rows: ReportRow[] = [];
  for (const contributor of contributors) {
    const account = contributor.account;
    if (!account) continue;
    const academic = account.academic;

    if (professorFilter && academic?.professorKey !== canonicalKey(professorFilter)) continue;
    if (groupFilter && academic?.groupKey !== canonicalKey(groupFilter)) continue;
    if (courseFilter && academic?.courseKey !== canonicalKey(courseFilter)) continue;

    const totals = aggregates.get(contributor.id);
    rows.push({
      nombre: account.fullName,
      correo: account.email,
      adscripcion: account.affiliation === "UMNG" ? "UMNG" : "Persona natural",
      verificado: account.academicVerified ? "sí" : "declarado",
      clase: academic?.course ?? "",
      grupo: academic?.group ?? "",
      profesor: academic?.professor ?? "",
      fotos_aprobadas: totals?.photos ?? 0,
      objetos_distintos: totals?.objects.size ?? 0,
      imagenes_distintas: totals?.images.size ?? 0,
      materiales_distintos: totals?.materials.size ?? 0,
      primer_aporte: totals?.first ?? "",
      ultimo_aporte: totals?.last ?? "",
    });
  }

  rows.sort((a, b) => b.fotos_aprobadas - a.fotos_aprobadas || a.nombre.localeCompare(b.nombre, "es"));

  if (format === "csv") {
    return {
      status: 200,
      headers: {
        "content-type": "text/csv; charset=utf-8",
        "content-disposition": 'attachment; filename="aportes-recycol.csv"',
      },
      body: toCsv(COLUMNS, rows),
    };
  }
  return { status: 200, jsonBody: { rows, generatedAt: new Date().toISOString() } };
}

/**
 * Trae las capturas aprobadas y agrega en memoria.
 *
 * A la escala de este proyecto —§10 apunta a 6 000–9 000 fotos— son unos pocos
 * miles de filas y el informe se genera de vez en cuando, no en cada carga de
 * página. Si algún día creciera un orden de magnitud, tocaría materializar
 * contadores por aportante como ya se hace con los de material.
 */
async function approvedFacts(): Promise<CaptureFact[]> {
  const facts: CaptureFact[] = [];
  await eachApproved((capture) => {
    facts.push({
      contributorId: capture.contributorId,
      objectId: capture.objectId,
      phash: capture.phash,
      material: capture.material,
      capturedAt: capture.capturedAt,
    });
  });
  return facts;
}

/**
 * Sugerencias de clase, grupo y profesor a partir de lo que ya escribieron
 * otros.
 *
 * Es el mismo remedio que la lista cerrada de materiales, adaptado a un dominio
 * que no podemos enumerar de antemano: no sabemos el catálogo de asignaturas de
 * la UMNG, pero sí podemos evitar que el informe salga partido entre «Cálculo
 * 1», «calculo I» y «CALCULO 1». Se propone lo que ya existe; escribir algo
 * nuevo sigue siendo posible.
 */
export async function academicSuggestions(request: HttpRequest): Promise<HttpResponseInit> {
  // Solo con sesión: es información de una comunidad concreta y no hace falta
  // exponerla a todo internet.
  if (!readPrincipal(request) && process.env.ALLOW_LOCAL_ADMIN !== "true") {
    return { status: 401, jsonBody: { message: "Hay que iniciar sesión" } };
  }

  const field = request.query.get("field");
  if (field !== "course" && field !== "group" && field !== "professor") {
    return { status: 400, jsonBody: { message: "Campo no válido" } };
  }

  await ensureTables();
  const contributors = await listAccountContributors();
  // Una entrada por clave normalizada, y como texto visible el que más veces se
  // escribió: así gana la ortografía mayoritaria en vez de la primera que llegó.
  const counts = new Map<string, Map<string, number>>();
  for (const contributor of contributors) {
    const academic = contributor.account?.academic;
    if (!academic) continue;
    const key = academic[`${field}Key` as const];
    const display = academic[field];
    if (!key || !display) continue;
    const variants = counts.get(key) ?? new Map<string, number>();
    variants.set(display, (variants.get(display) ?? 0) + 1);
    counts.set(key, variants);
  }

  const suggestions = [...counts.values()]
    .map((variants) => [...variants.entries()].sort((a, b) => b[1] - a[1])[0]!)
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "es"))
    .slice(0, 50)
    .map(([display]) => display);

  return {
    status: 200,
    headers: { "cache-control": "private, max-age=120" },
    jsonBody: { suggestions },
  };
}

app.http("academicReport", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "api/report/academic",
  handler: academicReport,
});

app.http("academicSuggestions", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "api/academic/suggestions",
  handler: academicSuggestions,
});
