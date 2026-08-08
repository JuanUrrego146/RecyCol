/**
 * Elegir cómo entrar. Y, sobre todo, poder no entrar.
 *
 * La cuenta sirve para una cosa concreta: que un profesor de la UMNG pueda ver
 * cuánto aportó cada quien y reconocérselo. Quien no necesite eso aporta igual
 * sin identificarse, y esa opción está aquí a la vista, no escondida — obligar a
 * registrarse es la barrera que más aportes mata, y el volumen es lo que el
 * modelo necesita.
 *
 * No hay formulario de registro ni contraseña porque no los manejamos: se entra
 * con una cuenta que ya existe y el proveedor responde por ella.
 */

import { LOGIN_PROVIDERS, loginUrl } from "../data/session";
import { Header, Notice } from "./components";

export function LoginScreen({
  onSkip,
  returnTo = "/",
}: {
  onSkip: () => void;
  returnTo?: string;
}) {
  return (
    <div className="screen">
      <Header
        right={
          <button type="button" className="button-ghost" onClick={onSkip}>
            Ahora no
          </button>
        }
      />

      <h1>Entra si quieres que te cuenten los aportes</h1>
      <p className="muted">
        Sirve para que tu profesor pueda ver cuántas fotos aportaste. Si no lo necesitas, puedes
        aportar sin dar tu nombre.
      </p>

      <div className="card">
        <h2>Soy de la UMNG</h2>
        <p className="muted">
          Entra con tu correo <strong>@unimilitar.edu.co</strong>. Así queda comprobado que eres de
          la universidad, sin que tengas que demostrarlo.
        </p>
        <a className="button button-primary button-block" href={loginUrl("aad", returnTo)}>
          Entrar con el correo de la universidad
        </a>
      </div>

      <div className="card">
        <h2>Soy persona natural</h2>
        <p className="muted">
          Cualquier cuenta Microsoft sirve —también las de Outlook o Hotmail—. Solo pediremos tu
          nombre.
        </p>
        {LOGIN_PROVIDERS.map((provider) => (
          <a
            key={provider.id}
            className="button button-secondary button-block"
            href={loginUrl(provider.id, returnTo)}
          >
            <span aria-hidden="true">{provider.glyph}</span> {provider.name}
          </a>
        ))}
        <p className="tiny">
          ¿No tienes ninguna? Puedes aportar sin cuenta; solo no saldrás en el reporte de tu
          profesor.
        </p>
      </div>

      <Notice tone="info">
        Nunca escribes una contraseña aquí. Te lleva a la página del proveedor, y nosotros solo
        recibimos tu nombre y tu correo.
      </Notice>

      <div className="actions">
        <button type="button" className="button button-secondary button-block" onClick={onSkip}>
          Aportar sin cuenta
        </button>
      </div>
    </div>
  );
}
