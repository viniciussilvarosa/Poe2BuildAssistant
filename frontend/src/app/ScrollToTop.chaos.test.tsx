import { act, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { routes } from "./routes";

/** Caos do PR #93 (#92): ScrollRestoration no Layout. */

function rolarPara(y: number) {
  Object.defineProperty(window, "scrollY", { configurable: true, value: y });
  Object.defineProperty(window, "pageYOffset", { configurable: true, value: y });
}

/** Uma página carregada do zero (link externo, endereço digitado, redirecionamento do login). */
function carregarDoZero(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  return { router, ...render(<RouterProvider router={router} />) };
}

describe("caos da rolagem ao trocar de página (#92)", () => {
  let scrollTo: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    scrollTo = vi.fn();
    window.scrollTo = scrollTo as unknown as typeof window.scrollTo;
    sessionStorage.clear();
    rolarPara(0);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    rolarPara(0);
  });

  describe("R1: posição de outra página carregada do zero", () => {
    // CHAOS: KILLED no PR #93. As duas cargas do zero têm a mesma chave de histórico ("default"),
    // então a segunda página herda a rolagem salva pela primeira. No navegador: /guia aberta do zero e
    // rolada até 3378, depois /criar digitada na barra de endereço abria em 311 (o fim do formulário).
    it("abrir /criar do zero depois de rolar /guia (também aberta do zero) começa no topo", async () => {
      const guia = carregarDoZero("/guia");
      rolarPara(1800);
      act(() => {
        window.dispatchEvent(new Event("pagehide"));
      });
      guia.unmount();
      rolarPara(0);
      scrollTo.mockClear();

      carregarDoZero("/criar");
      await screen.findByRole("heading", { level: 1 });

      expect(scrollTo).not.toHaveBeenCalledWith(0, 1800);
    });
  });

  describe("R5: armazenamento da sessão indisponível", () => {
    it("sessionStorage que lança erro (navegação privada, bloqueio) não derruba o app nem a navegação", async () => {
      vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
        throw new DOMException("bloqueado", "SecurityError");
      });
      vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
        throw new DOMException("bloqueado", "SecurityError");
      });

      const { router } = carregarDoZero("/guia");
      rolarPara(900);
      await act(() => router.navigate("/minhas-builds"));
      act(() => {
        window.dispatchEvent(new Event("pagehide"));
      });

      expect(router.state.location.pathname).toBe("/minhas-builds");
      expect(await screen.findByRole("heading", { level: 1 })).toBeInTheDocument();
      expect(scrollTo).toHaveBeenLastCalledWith(0, 0);
    });
  });
});
