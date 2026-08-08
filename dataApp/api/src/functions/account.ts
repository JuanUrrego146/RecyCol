/**
 * Cuenta del aportante: leer el perfil y guardarlo.
 *
 * `GET  /api/me`         — quién soy y qué tengo guardado.
 * `POST /api/me/profile` — nombre, adscripción y datos académicos.
 *
 * Aquí no hay contraseñas ni registro: Static Web Apps resuelve la
 * autenticación y esta función solo guarda lo que la persona escribe **sobre**
 * esa identidad ya comprobada. El correo y la verificación académica no se
 * aceptan del cuerpo de la petición, se deducen de la sesión.
 */

import { app, HttpRequest, HttpResponseInit, InvocationContext } from "@azure/functions";
import { readPrincipal } from "../auth";
import { capturesContainer, contributorsContainer, isNotFound } from "../cosmos";
import {
  ContributorDocument,
  ValidationError,
  accountIdFor,
  assignSplit,
  isAccountId,
  parseProfile,
  todayStamp,
} from "../model";

export async function getMe(request: HttpRequest): Promise<HttpResponseInit> {
  const principal = readPrincipal(request);
  if (!principal) return { status: 200, jsonBody: { signedIn: false, contributor: null } };

  const id = accountIdFor(principal.userId);
  const contributor = await readContributor(id);

  return {
    status: 200,
    jsonBody: {
      signedIn: true,
      contributorId: id,
      provider: principal.identityProvider,
      email: principal.userDetails,
      profile: contributor?.account ?? null,
      capturesRegistered: contributor?.capturesRegistered ?? 0,
    },
  };
}

export async function putProfile(
  request: HttpRequest,
  context: InvocationContext,
): Promise<HttpResponseInit> {
  const principal = readPrincipal(request);
  if (!principal) {
    return { status: 401, jsonBody: { message: "Hay que iniciar sesión" } };
  }

  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;

  let profile;
  try {
    profile = parseProfile(body, {
      provider: principal.identityProvider,
      email: principal.userDetails,
    });
  } catch (error) {
    if (error instanceof ValidationError) {
      return { status: 400, jsonBody: { message: error.message } };
    }
    throw error;
  }

  const id = accountIdFor(principal.userId);
  const container = contributorsContainer();
  const existing = await readContributor(id);

  if (existing) {
    await container.item(id, id).patch([
      { op: "set", path: "/account", value: profile },
      { op: "set", path: "/lastSeenAt", value: new Date().toISOString() },
    ]);
  } else {
    const created: ContributorDocument = {
      id,
      split: assignSplit(id),
      createdAt: new Date().toISOString(),
      lastSeenAt: new Date().toISOString(),
      consentVersion: typeof body?.consentVersion === "string" ? body.consentVersion : "2.0",
      capturesRegistered: 0,
      quotaDay: todayStamp(),
      quotaUsed: 0,
      account: profile,
      linkedContributorIds: [],
    };
    try {
      await container.items.create(created);
    } catch (error) {
      if ((error as { code?: number }).code !== 409) throw error;
    }
  }

  // Enlace con lo aportado antes de entrar, si lo hubo.
  const linkId = typeof body?.linkAnonymousId === "string" ? body.linkAnonymousId : null;
  let linked = false;
  if (linkId && !isAccountId(linkId) && linkId !== id) {
    linked = await linkAnonymousContributor(id, linkId, context);
  }

  context.log(`Perfil guardado ${id} (${profile.affiliation}, verificado=${profile.academicVerified})`);
  return { status: 200, jsonBody: { contributorId: id, profile, linked } };
}

async function readContributor(id: string): Promise<ContributorDocument | undefined> {
  try {
    const { resource } = await contributorsContainer().item(id, id).read<ContributorDocument>();
    return resource;
  } catch (error) {
    if (isNotFound(error)) return undefined;
    throw error;
  }
}

/**
 * Une un aportante anónimo a una cuenta.
 *
 * El caso normal: alguien abre el enlace, aporta unas fotos, y solo después
 * entra en su cuenta para que le cuenten los puntos. Sin unirlos, esa persona
 * son dos aportantes en los datos, y §10 pide justo lo contrario.
 *
 * **La regla al unir: ante la duda, nunca control.** Si los dos lados cayeron en
 * particiones distintas, todo pasa a `TRAIN`, incluidas las capturas ya
 * guardadas del lado que estaba en `CONTROL`. Degradar a entrenamiento siempre
 * es seguro; lo que no se puede es dejar en el control propio a alguien que
 * también aparece en entrenamiento, porque entonces ese control deja de medir
 * generalización y §10 avisa de que ahí se destruye la evidencia.
 */
async function linkAnonymousContributor(
  accountId: string,
  anonymousId: string,
  context: InvocationContext,
): Promise<boolean> {
  const anonymous = await readContributor(anonymousId);
  if (!anonymous || anonymous.account) return false;

  const account = await readContributor(accountId);
  if (!account) return false;
  if (account.linkedContributorIds.includes(anonymousId)) return true;

  const container = contributorsContainer();
  await container
    .item(accountId, accountId)
    .patch([{ op: "add", path: "/linkedContributorIds/-", value: anonymousId }]);

  if (anonymous.split !== account.split) {
    context.warn(
      `Partición en conflicto al unir ${anonymousId} con ${accountId}: se degrada todo a TRAIN`,
    );
    await demoteToTrain(accountId);
    await demoteToTrain(anonymousId);
  }
  return true;
}

/** Pasa un aportante y todas sus capturas a `TRAIN`. Solo se mueve en esa dirección. */
async function demoteToTrain(contributorId: string): Promise<void> {
  await contributorsContainer()
    .item(contributorId, contributorId)
    .patch([{ op: "set", path: "/split", value: "TRAIN" }]);

  const { resources } = await capturesContainer()
    .items.query<{ id: string }>(
      {
        query: "SELECT c.id FROM c WHERE c.split = 'CONTROL'",
      },
      { partitionKey: contributorId },
    )
    .fetchAll();

  for (const capture of resources) {
    await capturesContainer()
      .item(capture.id, contributorId)
      .patch([{ op: "set", path: "/split", value: "TRAIN" }]);
  }
}

app.http("getMe", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "me",
  handler: getMe,
});

app.http("putProfile", {
  methods: ["POST"],
  authLevel: "anonymous",
  route: "me/profile",
  handler: putProfile,
});
