/**
 * Punto de entrada de las funciones.
 *
 * El modelo v4 de Azure Functions registra cada función al importar su módulo,
 * así que basta con importarlos aquí. `package.json` apunta `main` a
 * `dist/src/index.js`.
 */

import "./functions/captures";
import "./functions/stats";
import "./functions/review";
import "./functions/exportData";
import "./functions/account";
import "./functions/report";
