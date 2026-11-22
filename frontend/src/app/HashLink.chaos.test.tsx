import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { routes } from "./routes";

/** Caos do PR #97 (#96): "Como funciona" com Link e hash na chave de rolagem. */

let rolouPara: string[];

beforeEach(() => {
  rolouPara = [];
  sessionStorage.clear();
  window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
  Element.prototype.scrollIntoView = vi.fn(function (this: Element) {
    rolouPara.push(this.id);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function abrir(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe("H1–H3: hash hostil num link aberto do zero", () => {
  it.each(["/#%E0%A4%A", "/#%", "/criar#%ZZ"])("%s não derruba o app", async (path) => {
    vi.spyOn(console, "error").mockImplementation(() => {});

    abrir(path);

    expect(await screen.findByRole("heading", { level: 1 })).toBeInTheDocument();
    expect(screen.queryByText(/Unexpected Application Error|URI malformed/i)).not.toBeInTheDocument();
  });

  it("hash de um elemento que não existe abre a página normalmente", async () => {
    abrir("/#nao-existe");

    expect(await screen.findByRole("heading", { level: 1 })).toBeInTheDocument();
    expect(rolouPara).toEqual([]);
  });

  it("HTML no hash não vira elemento na página", async () => {
    abrir("/#<img src=x onerror=alert(1)>");
    await screen.findByRole("heading", { level: 1 });

    expect(document.querySelector('img[src="x"]')).toBeNull();
  });
});

describe("H4: clicar de novo já estando na seção", () => {
  it("depois de rolar para cima, o link rola até a seção outra vez", async () => {
    abrir("/guia");
    await screen.findByRole("heading", { level: 1 });
    await userEvent.click(screen.getByRole("link", { name: "Como funciona" }));
    await vi.waitFor(() => expect(rolouPara).toContain("como-funciona"));
    rolouPara = [];

    await userEvent.click(screen.getByRole("link", { name: "Como funciona" }));

    await vi.waitFor(() => expect(rolouPara).toContain("como-funciona"));
  });
});

describe("H5: voltar e avançar", () => {
  it("Voltar devolve a página anterior na posição dela; Avançar volta à seção", async () => {
    const router = abrir("/guia");
    await screen.findByRole("heading", { level: 1 });
    Object.defineProperty(window, "scrollY", { configurable: true, value: 1500 });
    await userEvent.click(screen.getByRole("link", { name: "Como funciona" }));
    await vi.waitFor(() => expect(rolouPara).toContain("como-funciona"));
    Object.defineProperty(window, "scrollY", { configurable: true, value: 700 });
    vi.mocked(window.scrollTo).mockClear();

    await act(() => router.navigate(-1));
    expect(router.state.location.pathname).toBe("/guia");
    expect(window.scrollTo).toHaveBeenLastCalledWith(0, 1500);

    await act(() => router.navigate(1));
    expect(router.state.location.hash).toBe("#como-funciona");
    expect(window.scrollTo).toHaveBeenLastCalledWith(0, 700);
    Object.defineProperty(window, "scrollY", { configurable: true, value: 0 });
  });
});
