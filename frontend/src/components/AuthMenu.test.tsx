import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../app/routes";
import { getMe, logout } from "../api/me";

vi.mock("../api/me");

function renderHome() {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ["/"] })} />);
  return screen.getByRole("navigation", { name: "Principal" });
}

describe("AuthMenu", () => {
  it("visitante vê 'Entrar com Google' apontando para /oauth2/authorization/google", async () => {
    vi.mocked(getMe).mockResolvedValue(null);
    renderHome();

    expect(await screen.findByRole("link", { name: "Entrar com Google" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google",
    );
  });

  it("usuário logado vê nome e botão Sair", async () => {
    vi.mocked(getMe).mockResolvedValue({ name: "Ana", role: "USER" });
    renderHome();

    expect(await screen.findByText("Ana")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sair" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Entrar com Google" })).toBeNull();
  });

  it("Sair faz POST /logout e volta a visitante", async () => {
    vi.mocked(getMe).mockResolvedValue({ name: "Ana", role: "USER" });
    vi.mocked(logout).mockResolvedValue(undefined);
    renderHome();

    await userEvent.click(await screen.findByRole("button", { name: "Sair" }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("link", { name: "Entrar com Google" })).toBeInTheDocument();
    expect(screen.queryByText("Ana")).toBeNull();
  });

  it("nome com HTML aparece como texto", async () => {
    vi.mocked(getMe).mockResolvedValue({ name: "<b>Ana</b>", role: "USER" });
    renderHome();

    expect(await screen.findByText("<b>Ana</b>")).toBeInTheDocument();
  });
});
