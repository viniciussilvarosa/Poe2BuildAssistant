import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { getCurrentDraft, getDraft } from "../../api/adminCatalog";
import { getCatalogOptions } from "../../api/catalog";
import { ApiError } from "../../api/client";
import { getMe, getMyBuilds, logout, type MyBuild } from "../../api/me";

vi.mock("../../api/me");
vi.mock("../../api/catalog");
vi.mock("../../api/adminCatalog");

/** Ataques ao login no frontend e a "Minhas builds" (card #15, PR #41). */

const BUILD: MyBuild = {
  id: "00000000-0000-0000-0000-00000000000b",
  createdAt: "2026-11-21T12:00:00Z",
  concept: "arqueiro de gelo",
  characterClass: "Ranger",
  ascendancy: "Deadeye",
  mainSkill: "Ice Shot",
};

function renderAt(path: string) {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />);
}

beforeEach(() => {
  vi.mocked(getMe).mockResolvedValue({ name: "Ana", role: "ADMIN" });
  vi.mocked(getMyBuilds).mockResolvedValue([BUILD]);
});

describe("H1 — sair num computador compartilhado", () => {
  it("se o logout falhar no servidor, a tela não finge que saiu", async () => {
    vi.mocked(logout).mockRejectedValue(new ApiError(403, "Não foi possível falar com o servidor."));
    renderAt("/");

    await userEvent.click(await screen.findByRole("button", { name: "Sair" }));

    // A sessão continua viva no servidor: mostrar "Entrar com Google" faria a pessoa ir embora logada.
    expect(screen.queryByRole("link", { name: "Entrar com Google" })).toBeNull();
    expect(await screen.findByText("Ana")).toBeInTheDocument();
  });

  it("depois de sair, a lista de builds da pessoa some da tela", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    renderAt("/minhas-builds");
    expect(await screen.findByText("Ice Shot")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Sair" }));

    expect(await within(screen.getByRole("navigation", { name: "Principal" })).findByRole("link", { name: "Entrar com Google" })).toBeInTheDocument();
    expect(screen.queryByText("Ice Shot")).toBeNull();
  });

  it("depois de sair na tela de admin, o catálogo some e aparece Acesso restrito", async () => {
    vi.mocked(logout).mockResolvedValue(undefined);
    vi.mocked(getCatalogOptions).mockResolvedValue({ patch: "0.5", classes: [], weaponTypes: [], skillGems: [], playstyles: [] });
    vi.mocked(getCurrentDraft).mockResolvedValue(null);
    vi.mocked(getDraft).mockReturnValue(new Promise(() => {}));
    renderAt("/admin");
    expect(await screen.findByText(/Versão publicada/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Sair" }));

    expect(await screen.findByRole("heading", { name: "Acesso restrito" })).toBeInTheDocument();
  });
});

describe("H2 — dados fora do esperado", () => {
  it("data inválida não derruba a página", async () => {
    vi.mocked(getMyBuilds).mockResolvedValue([{ ...BUILD, createdAt: "ontem" }]);
    renderAt("/minhas-builds");

    expect(await screen.findByText("Ice Shot")).toBeInTheDocument();
  });

  it("emoji no limite do resumo não vira caractere quebrado", async () => {
    vi.mocked(getMyBuilds).mockResolvedValue([{ ...BUILD, concept: "a".repeat(59) + "🧊 gelo eterno" }]);
    renderAt("/minhas-builds");

    const [item] = await screen.findAllByRole("listitem");
    expect(item.textContent).not.toMatch(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/);
  });

  it("falha 500 mostra a mensagem, sem lista e sem convite de login", async () => {
    vi.mocked(getMyBuilds).mockRejectedValue(new ApiError(500, "Algo deu errado. Tente novamente."));
    renderAt("/minhas-builds");

    expect(await screen.findByRole("alert")).toHaveTextContent("Algo deu errado");
    expect(within(screen.getByRole("main")).queryByRole("link", { name: "Entrar com Google" })).toBeNull();
  });
});
