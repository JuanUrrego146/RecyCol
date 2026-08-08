/**
 * Latido: existir cada cinco minutos para que no haya arranque en frío.
 *
 * ## El problema que resuelve
 *
 * El plan de consumo desasigna la instancia tras unos veinte minutos sin
 * tráfico. La siguiente visita paga el arranque entero: levantar el worker de
 * Node y cargar sus módulos desde el recurso compartido de Azure Files que usa
 * el plan. Juan lo midió abriendo el enlace: **cuarenta segundos**. En caliente
 * la misma página tarda 0,3 s.
 *
 * Cuarenta segundos es letal para un enlace que se reparte por WhatsApp: nadie
 * espera, y quien espera una vez no vuelve.
 *
 * ## Por qué así y no de otra forma
 *
 * Lo que de verdad arregla esto es «siempre encendido», y **no existe en el plan
 * de consumo**: el disparador de precalentamiento de Functions es exclusivo de
 * Premium y Dedicado. Los planes que sí lo dan empiezan en unos 13 USD/mes, trece
 * veces el presupuesto de este proyecto.
 *
 * Un temporizador que se dispare más a menudo que el tiempo de desasignación
 * mantiene la instancia viva, y cuesta cero: 12 ejecuciones por hora son 8.640 al
 * mes, un 0,9 % del millón gratuito.
 *
 * No es una garantía —la plataforma puede reciclar la instancia igualmente, y
 * tras cada despliegue el primer arranque sigue siendo frío—, pero convierte
 * «cuarenta segundos casi siempre» en «cuarenta segundos casi nunca».
 *
 * ## Por qué no hace nada
 *
 * A propósito. Si tocara el almacenamiento cargaría los SDK de Azure y pagaría
 * transacciones cada cinco minutos, todo el día, para nada. Lo único que hace
 * falta es que el host tenga una razón para seguir vivo.
 */

import { app, InvocationContext, Timer } from "@azure/functions";

export async function keepWarm(_timer: Timer, context: InvocationContext): Promise<void> {
  context.debug("latido");
}

app.timer("keepWarm", {
  // NCRONTAB de seis campos —el primero son segundos—: cada cinco minutos.
  schedule: "0 */5 * * * *",
  // Sin disparo al arrancar: provoca reinicios en cadena si algo va mal.
  runOnStartup: false,
  // Sin monitor no hay arrendamiento de blob ni recuperación de disparos
  // perdidos. Aquí ninguna de las dos cosas importa —perderse un latido no tiene
  // consecuencia— y se ahorra una escritura al almacenamiento cada vez.
  useMonitor: false,
  handler: keepWarm,
});
