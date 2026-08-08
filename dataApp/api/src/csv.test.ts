/**
 * El informe académico se abre en Excel para poner notas, y lleva nombres y
 * asignaturas escritos por quien se registró. Que una celda no se ejecute al
 * abrirla no es un detalle de formato: es la diferencia entre exportar datos y
 * ejecutar el texto de un desconocido en la máquina de quien califica.
 */

import { describe, expect, it } from "vitest";
import { csvCell, toCsv } from "./csv";

describe("celdas", () => {
  it("deja en paz lo que no necesita nada", () => {
    expect(csvCell("Juan Pérez")).toBe("Juan Pérez");
    expect(csvCell(42)).toBe("42");
    expect(csvCell(true)).toBe("true");
  });

  it("vacía los nulos", () => {
    expect(csvCell(null)).toBe("");
    expect(csvCell(undefined)).toBe("");
  });

  it("entrecomilla comas, comillas y saltos de línea", () => {
    expect(csvCell("Pérez, Juan")).toBe('"Pérez, Juan"');
    expect(csvCell('dijo "hola"')).toBe('"dijo ""hola"""');
    expect(csvCell("dos\nlíneas")).toBe('"dos\nlíneas"');
  });

  it("neutraliza los cuatro comienzos de fórmula", () => {
    expect(csvCell('=HYPERLINK("http://malo","Haz clic")')).toBe(
      '"\'=HYPERLINK(""http://malo"",""Haz clic"")"',
    );
    expect(csvCell("+1234")).toBe("'+1234");
    expect(csvCell("-2+3")).toBe("'-2+3");
    expect(csvCell("@SUM(A1:A9)")).toBe("'@SUM(A1:A9)");
  });

  it("neutraliza también tabulador y retorno de carro al principio", () => {
    // Excel los salta y evalúa lo que venga detrás.
    expect(csvCell("\t=1+1")).toBe("'\t=1+1");
    expect(csvCell("\r=1+1")).toBe("'\r=1+1");
  });

  it("no toca un signo que no está al principio", () => {
    expect(csvCell("Cálculo 1 - grupo A")).toBe("Cálculo 1 - grupo A");
    expect(csvCell("correo@ejemplo.com")).toBe("correo@ejemplo.com");
  });
});

describe("filas", () => {
  const columnas = ["nombre", "fotos"] as const;
  const filas = [
    { nombre: "Ana", fotos: 3 },
    { nombre: "=cmd|'/c calc'!A0", fotos: 1 },
  ];

  it("escribe cabecera y filas", () => {
    expect(toCsv(columnas, filas)).toBe("nombre,fotos\nAna,3\n'=cmd|'/c calc'!A0,1\n");
  });

  it("omite la cabecera en las páginas siguientes", () => {
    expect(toCsv(columnas, filas, false)).toBe("Ana,3\n'=cmd|'/c calc'!A0,1\n");
  });

  it("devuelve cadena vacía si no hay ni cabecera ni filas", () => {
    expect(toCsv(columnas, [], false)).toBe("");
  });
});
