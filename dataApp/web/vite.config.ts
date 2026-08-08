import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Base "/" porque Static Web Apps sirve la aplicación en la raíz del dominio.
// El proxy de /api solo aplica en desarrollo: en producción Static Web Apps
// enruta /api a las funciones gestionadas sin configuración adicional.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    fs: {
      // `domain/profile.ts` importa shared/resources/profiles/co.json para no
      // duplicar las reglas normativas (RNF-004). Vive fuera de dataApp/, así
      // que el servidor de desarrollo necesita permiso explícito para servirlo.
      allow: ["../.."],
    },
    proxy: {
      "/api": {
        target: "http://127.0.0.1:7071",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
  },
});
