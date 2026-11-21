import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { getBuild, type BuildContent, type SavedBuild } from "../../api/builds";
import { ApiError } from "../../api/client";
import { buildFixture } from "./fixtures";
import { formatBuildSummary } from "./formatBuildSummary";

vi.mock("../../api/builds");

/** Ataques à tela Sua build sugerida (card #13, PR #33). */

function renderWith(build: SavedBuild) {
  const router = createMemoryRouter(routes, { initialEntries: [{ pathname: `/builds/${build.id}`, state: { build } }] });
  render(<RouterProvider router={router} />);
  return router;
}

/** O LLM preenche choiceReasons livremente e o validador não confere esses campos. */
function withReasons(choiceReasons: unknown[]) {
  return buildFixture({ choiceReasons } as unknown as Partial<BuildContent>);
}

afterEach(() => {
  Object.defineProperty(navigator, "clipboard", { value: undefined, configurable: true });
});

describe("C1 — justificativas da IA fora do formato", () => {
  it("field nulo não derruba a página", () => {
    renderWith(withReasons([{ field: null, reason: "Escolhi pela mobilidade." }]));

    expect(screen.getByRole("heading", { name: "Gemas" })).toBeInTheDocument();
    expect(screen.getByText("Escolhi pela mobilidade.")).toBeInTheDocument();
  });

  it("reason nulo ou vazio não vira uma linha vazia", () => {
    renderWith(withReasons([{ field: "characterClass", reason: null }, { field: "ascendancy", reason: "  " }]));

    expect(screen.getByRole("heading", { name: "Gemas" })).toBeInTheDocument();
    expect(screen.queryByText("Classe")).toBeNull();
    expect(screen.queryByText("Ascendência")).toBeNull();
  });
});

describe("C2 — trocar de build sem sair da tela", () => {
  it("indo de A para B com a build no estado, mostra B", async () => {
    const a = buildFixture();
    const b: SavedBuild = { ...buildFixture({ mainSkill: "Fireball", characterClass: "Witch" }), id: "00000000-0000-0000-0000-0000000000bb" };
    const router = renderWith(a);

    await act(() => router.navigate(`/builds/${b.id}`, { state: { build: b } }));

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Fireball");
  });

  it("indo de A para B por link direto, busca e mostra B", async () => {
    const b: SavedBuild = { ...buildFixture({ mainSkill: "Fireball" }), id: "00000000-0000-0000-0000-0000000000bb" };
    vi.mocked(getBuild).mockResolvedValue(b);
    const router = renderWith(buildFixture());

    await act(() => router.navigate(`/builds/${b.id}`));

    expect(await screen.findByRole("heading", { level: 1, name: "Fireball" })).toBeInTheDocument();
    expect(getBuild).toHaveBeenCalledWith(b.id);
  });
});

describe("C3 — navegador e rede hostis", () => {
  it("sem navigator.clipboard (HTTP sem TLS) cai na cópia manual", async () => {
    Object.defineProperty(navigator, "clipboard", { value: undefined, configurable: true });
    renderWith(buildFixture());

    await userEvent.click(screen.getByRole("button", { name: "Copiar Resumo" }));

    expect(await screen.findByLabelText(/Copie o resumo/)).toHaveValue(formatBuildSummary(buildFixture()));
  });

  it("falha 500 no link direto mostra a mensagem e o caminho para criar outra", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    vi.mocked(getBuild).mockRejectedValue(new ApiError(500, "Algo deu errado. Tente novamente."));
    const router = createMemoryRouter(routes, { initialEntries: ["/builds/00000000-0000-0000-0000-000000000099"] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByText("Algo deu errado. Tente novamente.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Criar nova build" })).toBeInTheDocument();
  });

  it("estado do histórico com build de outro id é ignorado: vale o id da URL", async () => {
    const outra: SavedBuild = { ...buildFixture({ mainSkill: "Fireball" }), id: "outra" };
    vi.mocked(getBuild).mockResolvedValue(buildFixture());
    const router = createMemoryRouter(routes, {
      initialEntries: [{ pathname: "/builds/00000000-0000-0000-0000-000000000042", state: { build: outra } }],
    });
    render(<RouterProvider router={router} />);

    expect(await screen.findByRole("heading", { level: 1, name: "Ice Shot" })).toBeInTheDocument();
  });
});
