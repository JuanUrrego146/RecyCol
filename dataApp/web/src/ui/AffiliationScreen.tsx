/**
 * «¿Eres de la Universidad Militar?» — una sola pregunta, una sola vez.
 *
 * ## Por qué existe
 *
 * Juan abrió la aplicación desplegada y dijo: «no pregunta antes si es de la
 * UMNG ni nada, eso está raro». Tenía razón. La vía estaba entera —acceso con
 * el correo institucional, acreditación por dominio, informe para el profesor—
 * pero enterrada tras un aviso lateral en la pantalla de inicio, entre las
 * subidas pendientes y los botones de misión. Si el objetivo de las cuentas es
 * que un profesor dé puntos, esa vía no puede ser una nota al pie.
 *
 * ## Por qué justo aquí y no en otro sitio
 *
 * Va inmediatamente después del consentimiento, antes de la primera misión. Es
 * el único momento en que la persona todavía no tiene nada que perder por
 * detenerse: una pantalla de más entre la foto y el guardado cuesta aportes, y
 * una pantalla al principio cuesta un toque.
 *
 * ## Lo que NO hace
 *
 * No obliga a nada. «Ahora no» lleva directo a la misión y no se vuelve a
 * preguntar. La decisión de Juan sigue en pie: **la cuenta es opcional**, porque
 * obligar a registrarse es la barrera que más aportes mata y el volumen es lo
 * que el modelo necesita.
 */

import { Header } from "./components";

export function AffiliationScreen({
  onUmng,
  onSkip,
}: {
  onUmng: () => void;
  onSkip: () => void;
}) {
  return (
    <div className="screen">
      <Header />

      <h1>¿Estudias o trabajas en la Universidad Militar?</h1>
      <p className="muted">
        Si entras con tu correo <strong>@unimilitar.edu.co</strong>, tus fotos quedan a tu nombre y
        tu profesor puede ver cuántas aportaste. Algunos dan puntos por esto.
      </p>

      <div className="card">
        <h2>Cómo funciona</h2>
        <p className="muted">
          Entras con el correo de la universidad —sin escribir ninguna contraseña aquí— y nos dices
          tu clase, tu grupo y quién es tu profesor. Nada más.
        </p>
        <p className="tiny">
          Tu profesor verá tu nombre y cuántas fotos tuyas se aprobaron. Ni las fotos, ni tu correo,
          ni nada de los demás.
        </p>
      </div>

      <div className="actions">
        <button type="button" className="button button-primary button-block" onClick={onUmng}>
          Sí, soy de la UMNG
        </button>
        <button type="button" className="button button-secondary button-block" onClick={onSkip}>
          No, o prefiero aportar sin cuenta
        </button>
        <p className="tiny">
          Puedes cambiar de idea cuando quieras desde la esquina de arriba. Aportar sin cuenta
          funciona igual de bien y cuenta igual para el proyecto.
        </p>
      </div>
    </div>
  );
}
