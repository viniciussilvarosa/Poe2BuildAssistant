/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Em dev, o Vite (5173) repassa a API e o login ao Spring (8080): mesma origem para o navegador,
// então o cookie de sessão e o CSRF funcionam igual à produção.
const backend = "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": backend,
      "/oauth2": backend,
      "/login": backend,
      "/logout": backend,
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: true,
  },
});
