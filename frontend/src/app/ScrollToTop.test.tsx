import { act, render } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { routes } from "./routes";

/** A página anterior estava rolada até o fim (ex.: o botão "Gerar Build com IA" no fim do formulário). */
function rolarPara(y: number) {
  Object.defineProperty(window, "scrollY", { configurable: true, value: y });
  Object.defineProperty(window, "pageYOffset", { configurable: true, value: y });
}

describe("posição da rolagem ao trocar de página (#92)", () => {
  let scrollTo: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    scrollTo = vi.fn();
    vi.stubGlobal("scrollTo", scrollTo);
    window.scrollTo = scrollTo as unknown as typeof window.scrollTo;
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    rolarPara(0);
  });

  it("navegar a partir de uma página rolada abre a nova página no topo", async () => {
    const router = createMemoryRouter(routes, { initialEntries: ["/guia"] });
    render(<RouterProvider router={router} />);
    rolarPara(1800);
    scrollTo.mockClear();

    await act(() => router.navigate("/minhas-builds"));

    expect(scrollTo).toHaveBeenLastCalledWith(0, 0);
  });

  it("voltar restaura a posição em que o usuário estava", async () => {
    const router = createMemoryRouter(routes, { initialEntries: ["/guia"] });
    render(<RouterProvider router={router} />);
    rolarPara(1800);
    await act(() => router.navigate("/minhas-builds"));
    rolarPara(0);
    scrollTo.mockClear();

    await act(() => router.navigate(-1));

    expect(scrollTo).toHaveBeenLastCalledWith(0, 1800);
  });
});
