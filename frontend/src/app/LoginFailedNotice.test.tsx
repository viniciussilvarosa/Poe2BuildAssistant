import { act, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";
import { routes } from "./routes";

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe("aviso de falha no login com Google (#63)", () => {
  it("?login=falhou mostra o aviso com o link para tentar de novo", async () => {
    renderAt("/?login=falhou");

    const alerta = await screen.findByRole("alert");
    expect(alerta).toHaveTextContent("Não foi possível entrar com o Google.");
    expect(screen.getByRole("link", { name: "Tentar de novo" })).toHaveAttribute("href", "/oauth2/authorization/google");
  });

  it("o parâmetro sai da URL depois de exibido, sem perder o resto", async () => {
    const router = renderAt("/criar?login=falhou&conceito=arqueiro");

    await screen.findByRole("alert");

    expect(router.state.location.search).toBe("?conceito=arqueiro");
    expect(router.state.location.pathname).toBe("/criar");
  });

  it("sem o parâmetro, ou com outro valor, nenhum aviso", () => {
    renderAt("/?login=<script>alert(1)</script>");

    expect(screen.queryByText(/Não foi possível entrar com o Google/)).not.toBeInTheDocument();
    expect(screen.queryByText(/script/)).not.toBeInTheDocument();
  });

  it("navegar para outra página tira o aviso", async () => {
    const router = renderAt("/?login=falhou");
    await screen.findByRole("alert");

    await act(() => router.navigate("/minhas-builds"));

    await waitFor(() => expect(screen.queryByText(/Não foi possível entrar com o Google/)).not.toBeInTheDocument());
  });
});
