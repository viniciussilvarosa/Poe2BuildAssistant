import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { routes } from "./routes";

/** "Como funciona" a partir de qualquer página leva à seção da home (#96). */
describe("link Como funciona (#96)", () => {
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

  it("a partir de outra página, abre a home rolada até a seção, sem recarregar o app", async () => {
    const router = abrir("/guia");
    await screen.findByRole("heading", { level: 1 });

    await userEvent.click(screen.getByRole("link", { name: "Como funciona" }));

    expect(router.state.location).toMatchObject({ pathname: "/", hash: "#como-funciona" });
    await vi.waitFor(() => expect(rolouPara).toContain("como-funciona"));
  });

  it("já na home, clicar de novo também rola até a seção", async () => {
    abrir("/");
    await screen.findByRole("heading", { level: 1 });

    await userEvent.click(screen.getByRole("link", { name: "Como funciona" }));

    await vi.waitFor(() => expect(rolouPara).toContain("como-funciona"));
  });

  it("aberta do zero em /#como-funciona, vai à seção mesmo com uma posição da home salva na aba", async () => {
    // A home foi aberta do zero antes, rolada até 900, e a aba guardou essa posição.
    const antes = abrir("/");
    await screen.findByRole("heading", { level: 1 });
    Object.defineProperty(window, "scrollY", { configurable: true, value: 900 });
    act(() => {
      window.dispatchEvent(new Event("pagehide"));
    });
    Object.defineProperty(window, "scrollY", { configurable: true, value: 0 });
    antes.dispose();
    document.body.innerHTML = "";
    rolouPara = [];

    abrir("/#como-funciona");
    await screen.findByRole("heading", { level: 1 });

    await vi.waitFor(() => expect(rolouPara).toContain("como-funciona"));
    expect(window.scrollTo).not.toHaveBeenCalledWith(0, 900);
  });
});
