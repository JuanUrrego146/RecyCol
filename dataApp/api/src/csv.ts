/**
 * Escritura de CSV.
 *
 * Existe como módulo propio porque el escapado estaba copiado en el informe
 * académico y en el manifiesto de ML, y le faltaba lo mismo a los dos.
 *
 * ## Comillas: que el CSV sea CSV
 *
 * Un valor con coma, comilla o salto de línea rompe el formato si va tal cual.
 * Se encierra entre comillas y las comillas internas se duplican, que es lo que
 * dice el RFC 4180.
 *
 * ## Comilla simple delante: que la hoja de cálculo no ejecute nada
 *
 * Esto es lo que faltaba. Excel, LibreOffice y Google Sheets no tratan un CSV
 * como datos: **evalúan** toda celda que empiece por `=`, `+`, `-`, `@`, tabulador
 * o retorno de carro. El informe académico lleva nombres, correos, asignaturas y
 * nombres de profesor escritos por quien se registró, y se abre en Excel para
 * poner notas. Alguien que se llame `=HYPERLINK("http://…","Haz clic")` —o algo
 * peor que un enlace— consigue que su texto se ejecute en la máquina de quien
 * califica. No hace falta ninguna vulnerabilidad de Excel: es el comportamiento
 * documentado de la hoja de cálculo.
 *
 * El remedio estándar es anteponer una comilla simple, que fuerza el valor a
 * texto. Se paga con una comilla visible al principio de esas celdas — un precio
 * que solo se paga en valores que de otro modo se ejecutarían.
 *
 * Los números de estos informes son todos no negativos (recuentos, nitidez,
 * tamaños), así que la regla de `-` no marca ninguna celda legítima. Si algún día
 * se exporta una magnitud con signo, esto le pondrá una comilla delante y habrá
 * que decidir qué duele menos.
 *
 * > La tercera copia de esto vive dentro de `infra/export.sh`, que es un guion
 * > autocontenido y no puede importar de aquí. Si cambia una, cambia la otra.
 */

/** Lo que una hoja de cálculo interpreta como principio de fórmula. */
const FORMULA_START = /^[=+\-@\t\r]/;

export function csvCell(value: unknown): string {
  if (value === null || value === undefined) return "";
  const raw = String(value);
  const text = FORMULA_START.test(raw) ? `'${raw}` : raw;
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/**
 * Une filas y columnas.
 *
 * `withHeader` es `false` en las páginas siguientes de una exportación paginada:
 * la cabecera se escribe una sola vez, al principio del archivo.
 */
export function toCsv<T>(
  columns: readonly (keyof T & string)[],
  rows: readonly T[],
  withHeader = true,
): string {
  const lines = withHeader ? [columns.join(",")] : [];
  for (const row of rows) {
    lines.push(columns.map((column) => csvCell(row[column])).join(","));
  }
  return lines.length > 0 ? lines.join("\n") + "\n" : "";
}
