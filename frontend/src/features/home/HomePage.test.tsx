import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe("HomePage", () => {
  it("exibe o botão Criar Build que navega para /criar", async () => {
    const router = renderAt("/");

    await userEvent.click(screen.getByRole("link", { name: "Criar Build" }));

    expect(router.state.location.pathname).toBe("/criar");
  });

  it("exibe o aviso legal da GGG", () => {
    renderAt("/");

    expect(screen.getByText("Projeto independente não afiliado à Grinding Gear Games.")).toBeInTheDocument();
  });

  it("leva o conceito digitado para o formulário", async () => {
    const router = renderAt("/");

    await userEvent.type(screen.getByLabelText("Que personagem você quer jogar?"), "arqueiro de gelo");
    await userEvent.click(screen.getByRole("link", { name: "Criar Build" }));

    expect(router.state.location.pathname).toBe("/criar");
    expect(new URLSearchParams(router.state.location.search).get("conceito")).toBe("arqueiro de gelo");
  });

  it("um exemplo preenche o campo de conceito", async () => {
    renderAt("/");

    await userEvent.click(screen.getByRole("button", { name: "monge de raios" }));

    expect(screen.getByLabelText("Que personagem você quer jogar?")).toHaveValue("monge de raios");
  });

  it("menu principal tem as seções do produto", () => {
    renderAt("/");

    const nav = screen.getByRole("navigation", { name: "Principal" });
    expect(nav).toHaveTextContent("Início");
    expect(nav).toHaveTextContent("Minhas builds");
    expect(nav).toHaveTextContent("Entrar");
  });

  it("rota desconhecida mostra página de não encontrado com caminho de volta", () => {
    renderAt("/rota-que-nao-existe");

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Página não encontrada");
    expect(screen.getByRole("link", { name: "Voltar ao início" })).toHaveAttribute("href", "/");
  });
});
