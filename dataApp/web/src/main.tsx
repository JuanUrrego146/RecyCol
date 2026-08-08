import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./ui/styles.css";

const container = document.getElementById("root");
if (!container) throw new Error("Falta el contenedor #root");

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// El service worker solo cachea el armazón de la aplicación. Las capturas
// pendientes no viven aquí sino en IndexedDB (`uploadQueue.ts`), que sobrevive
// a cierres del navegador; un service worker no serviría para eso.
if ("serviceWorker" in navigator && import.meta.env.PROD) {
  window.addEventListener("load", () => {
    void navigator.serviceWorker.register("/sw.js");
  });
}
