/**
 * `GET /api/stats` — avance por clase, para las misiones y la barra de progreso.
 *
 * Público y sin datos personales: solo cuántas fotos hay de cada material y
 * cuántas personas han aportado. Se cachea 60 s en el borde porque la pantalla de
 * inicio se abre en cada aporte y el número no necesita ser exacto al segundo.
 */

import { app, HttpResponseInit, InvocationContext } from "@azure/functions";
import { contributorsContainer } from "../cosmos";
import { readTally } from "../stats";

export async function getStats(
  _request: unknown,
  context: InvocationContext,
): Promise<HttpResponseInit> {
  try {
    const [tally, contributors] = await Promise.all([readTally(), countContributors()]);
    return {
      status: 200,
      headers: { "cache-control": "public, max-age=60" },
      jsonBody: { tally, contributors, updatedAt: new Date().toISOString() },
    };
  } catch (error) {
    context.error("No se pudieron leer las estadísticas", error);
    return { status: 503, jsonBody: { message: "Estadísticas no disponibles" } };
  }
}

async function countContributors(): Promise<number> {
  const { resources } = await contributorsContainer()
    .items.query<number>({ query: "SELECT VALUE COUNT(1) FROM c" })
    .fetchAll();
  return resources[0] ?? 0;
}

app.http("getStats", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "stats",
  handler: getStats,
});
