/**
 * Modelo de datos y validación de entrada.
 *
 * `POST /api/captures` es un extremo **público**: cualquiera con el enlace puede
 * llamarlo con cualquier cuerpo. Todo lo que llega se valida campo a campo antes
 * de tocar la base, y lo que no encaje se rechaza entero. Guardar un registro a
 * medias contamina la fuente de datos que este proyecto está construyendo, que
 * es precisamente el problema que arrastra desde Garbage v2.
 *
 * La taxonomía se repite aquí a propósito, en vez de importarse de `web/`: son
 * dos paquetes que se despliegan por separado y compartir código entre ellos
 * añadiría un paso de build a cambio de nada. Si el enum del dominio cambia, hay
 * que tocar los dos y la prueba `model.test.ts` lo recuerda.
 */

export const MATERIALS = [
  "PLASTIC",
  "PAPER",
  "CARDBOARD",
  "BEVERAGE_CARTON",
  "GLASS",
  "METAL",
  "ORGANIC",
  "TEXTILE",
  "BATTERY",
  "ELECTRONIC",
  "RESIDUAL",
] as const;
export type Material = (typeof MATERIALS)[number];

export const CONTAMINATION_STATES = ["CLEAN", "RESIDUE", "LIQUID", "GREASE"] as const;
export type ContaminationState = (typeof CONTAMINATION_STATES)[number];

export const LIGHT_CONDITIONS = ["INDOOR", "OUTDOOR", "LOW_LIGHT", "BACKLIT"] as const;
export const ANGLES = ["TOP_DOWN", "OBLIQUE", "SIDE"] as const;
export const PHYSICAL_STATES = ["INTACT", "DEFORMED", "BROKEN"] as const;
export const BACKGROUNDS = ["BIN", "GROUND", "TABLE", "BAG", "OTHER"] as const;
export const CAPTURE_MODES = ["MISSION", "FREE"] as const;

/** Clases donde el estado de contaminación es obligatorio. Espejo de `web/src/domain/contamination.ts`. */
export const CONTAMINATION_REQUIRED_FOR: readonly Material[] = [
  "PAPER",
  "CARDBOARD",
  "BEVERAGE_CARTON",
];

/**
 * Versiones del consentimiento que se aceptan hoy. Una versión retirada deja de
 * admitir aportes nuevos, pero no cambia el permiso de los que ya entraron.
 *
 * La 1.0 no está porque **nunca llegó a producción**: prometía que no se pedía
 * nombre ni cuenta, y las cuentas llegaron antes del despliegue. No hay ni un
 * aporte hecho bajo ese texto.
 */
export const ACCEPTED_CONSENT_VERSIONS = ["2.0"] as const;

/**
 * Prefijo de los identificadores de aportante con cuenta.
 *
 * Separa de un vistazo las dos poblaciones —cuenta y anónimo— sin consultar la
 * base, y eso es lo que permite rechazar barato un aporte anónimo que dice venir
 * de una cuenta.
 */
export const ACCOUNT_ID_PREFIX = "acc-";

export function accountIdFor(userId: string): string {
  return `${ACCOUNT_ID_PREFIX}${userId}`;
}

export function isAccountId(contributorId: string): boolean {
  return contributorId.startsWith(ACCOUNT_ID_PREFIX);
}

export type Affiliation = "UMNG" | "GENERAL";

export interface AcademicInfo {
  course: string;
  group: string;
  professor: string;
  /** Claves normalizadas, para agrupar el informe sin que «Cálculo 1» y «calculo I» salgan separados. */
  courseKey: string;
  groupKey: string;
  professorKey: string;
}

export interface AccountProfile {
  provider: string;
  email: string;
  fullName: string;
  affiliation: Affiliation;
  /** `true` solo si el correo institucional lo acredita. Declararlo a mano no basta. */
  academicVerified: boolean;
  academic: AcademicInfo | null;
  updatedAt: string;
}

export const MAX_NAME_LENGTH = 80;
export const MAX_ACADEMIC_FIELD_LENGTH = 60;
export const UMNG_EMAIL_DOMAIN = "unimilitar.edu.co";

export function isUmngEmail(email: string): boolean {
  const at = email.lastIndexOf("@");
  if (at < 0) return false;
  const domain = email.slice(at + 1).trim().toLowerCase();
  return domain === UMNG_EMAIL_DOMAIN || domain.endsWith(`.${UMNG_EMAIL_DOMAIN}`);
}

const CONTROL_MAX = 0x1f;
const DELETE_CHAR = 0x7f;

export function normalizeText(value: string, maxLength: number): string {
  const withoutControls = Array.from(value, (character) => {
    const code = character.codePointAt(0) ?? 0;
    return code <= CONTROL_MAX || code === DELETE_CHAR ? " " : character;
  }).join("");
  return withoutControls.replace(/\s+/g, " ").trim().slice(0, maxLength);
}

/** Espejo de `canonicalKey` en `web/src/domain/account.ts`. */
export function canonicalKey(value: string): string {
  return normalizeText(value, 200)
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/**
 * Valida el perfil que llega de la aplicación.
 *
 * Ni el correo ni la verificación académica vienen del cuerpo: salen de la
 * identidad que inyecta Static Web Apps. Aceptarlos del cliente permitiría a
 * cualquiera declararse estudiante verificado de la UMNG, que es exactamente lo
 * que el profesor va a dar por bueno.
 */
export function parseProfile(
  body: unknown,
  identity: { provider: string; email: string },
): AccountProfile {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("El cuerpo debe ser un objeto JSON");
  }
  const input = body as Record<string, unknown>;

  const fullName = normalizeText(requireString(input.fullName, "fullName", 200), MAX_NAME_LENGTH);
  if (fullName.length < 3) {
    throw new ValidationError("Escribe tu nombre completo");
  }

  const affiliation = requireEnum(input.affiliation, ["UMNG", "GENERAL"] as const, "affiliation");

  let academic: AcademicInfo | null = null;
  if (affiliation === "UMNG") {
    const raw = input.academic as Record<string, unknown> | undefined;
    if (typeof raw !== "object" || raw === null) {
      throw new ValidationError("Faltan la clase, el grupo y el profesor");
    }
    const course = normalizeText(
      requireString(raw.course, "academic.course", 200),
      MAX_ACADEMIC_FIELD_LENGTH,
    );
    const group = normalizeText(
      requireString(raw.group, "academic.group", 200),
      MAX_ACADEMIC_FIELD_LENGTH,
    );
    const professor = normalizeText(
      requireString(raw.professor, "academic.professor", 200),
      MAX_ACADEMIC_FIELD_LENGTH,
    );
    if (course.length === 0 || group.length === 0 || professor.length === 0) {
      throw new ValidationError("La clase, el grupo y el profesor no pueden ir vacíos");
    }
    academic = {
      course,
      group,
      professor,
      courseKey: canonicalKey(course),
      groupKey: canonicalKey(group),
      professorKey: canonicalKey(professor),
    };
  }

  return {
    provider: identity.provider,
    email: identity.email,
    fullName,
    affiliation,
    // No se acepta del cliente: se deduce del correo con el que entró.
    academicVerified: affiliation === "UMNG" && isUmngEmail(identity.email),
    academic,
    updatedAt: new Date().toISOString(),
  };
}

export const SUPPORTED_SCHEMA_VERSIONS = [1] as const;

/** Espejo de `USABLE_LUMINANCE_*` en `web/src/capture/qualityGate.ts`. */
export const BLANK_LUMINANCE_FLOOR = 0.02;
export const BLANK_LUMINANCE_CEILING = 0.99;

export const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
export const MAX_NOTE_LENGTH = 120;
/** Tope diario por aportante. Un freno al ruido y a los aportes automatizados, no una meta. */
export const DAILY_CAPTURE_LIMIT = 400;

export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED";
export type DataSplit = "TRAIN" | "CONTROL";

export interface CaptureRecord {
  schemaVersion: number;
  id: string;
  contributorId: string;
  objectId: string;
  consentVersion: string;
  material: Material;
  contamination: ContaminationState | null;
  light: string;
  angle: string;
  physicalState: string | null;
  background: string | null;
  mode: string;
  requestedMaterial: Material | null;
  note: string | null;
  labelLatencyMs: number;
  quality: { sharpness: number; luminance: number; accepted: boolean };
  phash: string;
  image: { width: number; height: number; bytes: number; mimeType: string };
  crop: null;
  device: {
    platform: string;
    screenWidth: number;
    screenHeight: number;
    pixelRatio: number;
    memoryGb: number | null;
    cores: number | null;
  };
  capturedAt: string;
}

/** Documento tal y como se guarda en Cosmos. La clave de partición es `contributorId`. */
export interface CaptureDocument extends CaptureRecord {
  blobPath: string;
  status: ReviewStatus;
  /** `true` cuando el cliente confirmó que la imagen llegó al almacenamiento. */
  imageUploaded: boolean;
  split: DataSplit;
  registeredAt: string;
  uploadedAt: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
}

export interface ContributorDocument {
  id: string;
  /**
   * Partición asignada **una sola vez, al registrarse**, y jamás recalculada.
   * §10 exige un segundo control propio congelado, aportado por personas que no
   * aparecen en el entrenamiento; si el reparto se recalculara al cambiar el
   * porcentaje, un aportante podría cruzar de train a control y arruinar
   * exactamente la garantía que este campo existe para dar.
   */
  split: DataSplit;
  createdAt: string;
  lastSeenAt: string;
  consentVersion: string;
  capturesRegistered: number;
  /** Día (YYYY-MM-DD) del contador diario, para el tope antiabuso. */
  quotaDay: string;
  quotaUsed: number;
  /** Perfil de la cuenta, o `null` en los aportantes anónimos. La cuenta es opcional. */
  account: AccountProfile | null;
  /**
   * Identificadores anónimos que resultaron ser esta misma persona, al entrar en
   * su cuenta desde un dispositivo donde ya había aportado.
   *
   * Sin esto, alguien que aporta primero sin cuenta y luego con ella cuenta como
   * dos aportantes distintos, y §10 pide justo lo contrario: la unidad de
   * partición es la persona.
   */
  linkedContributorIds: string[];
}

export class ValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ValidationError";
  }
}

const ID_PATTERN = /^[a-zA-Z0-9-]{8,64}$/;
const PHASH_PATTERN = /^[0-9a-f]{16}$/;

function requireString(value: unknown, field: string, maxLength = 200): string {
  if (typeof value !== "string" || value.length === 0 || value.length > maxLength) {
    throw new ValidationError(`Campo inválido: ${field}`);
  }
  return value;
}

function requireId(value: unknown, field: string): string {
  const text = requireString(value, field, 64);
  if (!ID_PATTERN.test(text)) throw new ValidationError(`Identificador inválido: ${field}`);
  return text;
}

function requireEnum<T extends string>(
  value: unknown,
  allowed: readonly T[],
  field: string,
): T {
  if (typeof value !== "string" || !(allowed as readonly string[]).includes(value)) {
    throw new ValidationError(`Valor no permitido en ${field}`);
  }
  return value as T;
}

function optionalEnum<T extends string>(
  value: unknown,
  allowed: readonly T[],
  field: string,
): T | null {
  if (value === null || value === undefined) return null;
  return requireEnum(value, allowed, field);
}

function requireNumber(value: unknown, field: string, min: number, max: number): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max) {
    throw new ValidationError(`Número fuera de rango en ${field}`);
  }
  return value;
}

function optionalNumber(value: unknown, field: string, min: number, max: number): number | null {
  if (value === null || value === undefined) return null;
  return requireNumber(value, field, min, max);
}

/**
 * Valida y normaliza el cuerpo de `POST /api/captures`.
 *
 * Devuelve un objeto nuevo: nunca se guarda lo que llegó tal cual, para que un
 * campo de más en la petición no acabe persistido.
 */
export function parseCaptureRecord(body: unknown): CaptureRecord {
  if (typeof body !== "object" || body === null) {
    throw new ValidationError("El cuerpo debe ser un objeto JSON");
  }
  const input = body as Record<string, unknown>;

  const schemaVersion = requireNumber(input.schemaVersion, "schemaVersion", 1, 1000);
  if (!(SUPPORTED_SCHEMA_VERSIONS as readonly number[]).includes(schemaVersion)) {
    throw new ValidationError(`Versión de esquema no soportada: ${schemaVersion}`);
  }

  const consentVersion = requireString(input.consentVersion, "consentVersion", 16);
  if (!(ACCEPTED_CONSENT_VERSIONS as readonly string[]).includes(consentVersion)) {
    throw new ValidationError(
      "El consentimiento aceptado ya no es válido. Vuelve a abrir la aplicación.",
    );
  }

  const material = requireEnum(input.material, MATERIALS, "material");
  const contamination = optionalEnum(input.contamination, CONTAMINATION_STATES, "contamination");
  if (CONTAMINATION_REQUIRED_FOR.includes(material) && contamination === null) {
    throw new ValidationError(`El estado de contaminación es obligatorio en ${material}`);
  }

  const quality = input.quality as Record<string, unknown> | undefined;
  if (typeof quality !== "object" || quality === null) {
    throw new ValidationError("Faltan las métricas de calidad");
  }
  // Segunda barrera contra fotogramas vacíos —cámara tapada, sensor sin
  // arrancar—. El cliente ya los frena, pero este extremo es público y un
  // cliente con un fallo puede mandarlos en serie. Los umbrales son espejo de
  // `isBlank` en web/src/capture/qualityGate.ts.
  const luminance = requireNumber(quality.luminance, "quality.luminance", 0, 1);
  const sharpness = requireNumber(quality.sharpness, "quality.sharpness", 0, 1);
  if (luminance < BLANK_LUMINANCE_FLOOR || luminance > BLANK_LUMINANCE_CEILING || sharpness === 0) {
    throw new ValidationError("El fotograma no contiene imagen aprovechable");
  }

  const image = input.image as Record<string, unknown> | undefined;
  if (typeof image !== "object" || image === null) {
    throw new ValidationError("Faltan los datos de la imagen");
  }
  const mimeType = requireString(image.mimeType, "image.mimeType", 40);
  if (mimeType !== "image/jpeg") {
    throw new ValidationError("Solo se aceptan imágenes JPEG");
  }

  const device = (input.device ?? {}) as Record<string, unknown>;
  const phash = requireString(input.phash, "phash", 16);
  if (!PHASH_PATTERN.test(phash)) throw new ValidationError("pHash inválido");

  const note =
    input.note === null || input.note === undefined
      ? null
      : requireString(input.note, "note", MAX_NOTE_LENGTH);

  if (input.crop !== null && input.crop !== undefined) {
    throw new ValidationError("La versión 1 no admite recortes: la foto se guarda completa");
  }

  return {
    schemaVersion,
    id: requireId(input.id, "id"),
    contributorId: requireId(input.contributorId, "contributorId"),
    objectId: requireId(input.objectId, "objectId"),
    consentVersion,
    material,
    contamination,
    light: requireEnum(input.light, LIGHT_CONDITIONS, "light"),
    angle: requireEnum(input.angle, ANGLES, "angle"),
    physicalState: optionalEnum(input.physicalState, PHYSICAL_STATES, "physicalState"),
    background: optionalEnum(input.background, BACKGROUNDS, "background"),
    mode: requireEnum(input.mode, CAPTURE_MODES, "mode"),
    requestedMaterial: optionalEnum(input.requestedMaterial, MATERIALS, "requestedMaterial"),
    note,
    labelLatencyMs: requireNumber(input.labelLatencyMs, "labelLatencyMs", 0, 3_600_000),
    quality: {
      sharpness,
      luminance,
      accepted: typeof quality.accepted === "boolean" ? quality.accepted : false,
    },
    phash,
    image: {
      width: requireNumber(image.width, "image.width", 1, 12_000),
      height: requireNumber(image.height, "image.height", 1, 12_000),
      bytes: requireNumber(image.bytes, "image.bytes", 1, MAX_IMAGE_BYTES),
      mimeType,
    },
    crop: null,
    device: {
      platform: typeof device.platform === "string" ? device.platform.slice(0, 60) : "desconocida",
      screenWidth: optionalNumber(device.screenWidth, "device.screenWidth", 0, 20_000) ?? 0,
      screenHeight: optionalNumber(device.screenHeight, "device.screenHeight", 0, 20_000) ?? 0,
      pixelRatio: optionalNumber(device.pixelRatio, "device.pixelRatio", 0, 10) ?? 1,
      memoryGb: optionalNumber(device.memoryGb, "device.memoryGb", 0, 1024),
      cores: optionalNumber(device.cores, "device.cores", 0, 512),
    },
    capturedAt: requireIsoDate(input.capturedAt),
  };
}

/**
 * Acepta marcas de tiempo con hasta un día de desfase en cualquier sentido: los
 * relojes de los móviles se van, y una foto encolada sin cobertura puede subirse
 * horas después de tomarse. Fuera de esa ventana la fecha no es de fiar.
 */
function requireIsoDate(value: unknown): string {
  const text = requireString(value, "capturedAt", 40);
  const parsed = Date.parse(text);
  if (Number.isNaN(parsed)) throw new ValidationError("Fecha de captura inválida");
  const skew = Math.abs(Date.now() - parsed);
  if (skew > 7 * 24 * 60 * 60 * 1000) {
    throw new ValidationError("La fecha de captura está demasiado lejos de la actual");
  }
  return new Date(parsed).toISOString();
}

/**
 * Reparto determinista de aportantes entre entrenamiento y control.
 *
 * Se calcula del identificador con FNV-1a, así que el mismo aportante cae
 * siempre en el mismo lado. El resultado se guarda en su documento y a partir de
 * ahí manda el documento, no esta función: cambiar `CONTROL_SHARE_PERCENT` afecta
 * solo a quien se registre después.
 *
 * §10, punto 3: el control propio debe venir de personas que no aparecen en
 * entrenamiento, así que el corte es **por persona**, nunca por imagen.
 */
export const CONTROL_SHARE_PERCENT = 15;

export function assignSplit(contributorId: string, sharePercent = CONTROL_SHARE_PERCENT): DataSplit {
  let hash = 0x811c9dc5;
  for (let i = 0; i < contributorId.length; i += 1) {
    hash ^= contributorId.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash % 100 < sharePercent ? "CONTROL" : "TRAIN";
}

/** Ruta del blob. Se agrupa por material y aportante para que un `azcopy` selectivo sea trivial. */
export function blobPathFor(record: CaptureRecord): string {
  return `${record.material}/${record.contributorId}/${record.id}.jpg`;
}

export function todayStamp(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}
