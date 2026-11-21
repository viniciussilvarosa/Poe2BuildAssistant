import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { getCatalogOptions, type CatalogOptions } from "../../api/catalog";

vi.mock("../../api/catalog");
// Fábrica própria: no PR #31 o módulo ainda não exporta generateBuild; no #32 o envio a dispara.
vi.mock("../../api/builds", () => ({ generateBuild: vi.fn(() => new Promise(() => {})) }));

/** Ataques ao formulário Configurar personagem (card #12, PR #31). */

const OPTIONS: CatalogOptions = {
  patch: "0.4",
  classes: [{ name: "Ranger", ascendancies: [{ name: "Deadeye" }] }],
  weaponTypes: [{ name: "Bow" }],
  skillGems: [{ name: "Ice Shot", weaponTypes: ["Bow"] }],
  playstyles: [],
};

function renderWithState(state: unknown) {
  const router = createMemoryRouter(routes, { initialEntries: [{ pathname: "/criar", state }] });
  render(<RouterProvider router={router} />);
  return router;
}

function sentRequest(router: ReturnType<typeof createMemoryRouter>) {
  return (router.state.location.state as { request?: Record<string, unknown> } | null)?.request;
}

beforeEach(() => {
  vi.mocked(getCatalogOptions).mockResolvedValue(OPTIONS);
});

describe("H1 — volta de erro e reenvio", () => {
  it("o conceito editado depois do erro é o que vai no novo pedido, não o antigo", async () => {
    const router = renderWithState({
      request: { concept: "arqueiro mágico", characterClass: "Ranger" },
      error: { status: 502, detail: "A geração falhou." },
    });
    await screen.findByRole("option", { name: "Ranger" });
    const campo = screen.getByLabelText("Conceito do personagem");

    await userEvent.clear(campo);
    await userEvent.type(campo, "bruxa de fogo");
    await userEvent.click(screen.getByRole("button", { name: "Gerar Build com IA" }));

    expect(sentRequest(router)).toEqual({ concept: "bruxa de fogo", characterClass: "Ranger" });
  });

  it("escolha que não existe mais no catálogo não vai escondida no pedido", async () => {
    // Ex.: o admin publicou um patch novo entre o erro e o reenvio, ou o catálogo nem carregou.
    const router = renderWithState({
      request: { concept: "druida", characterClass: "Druid", ascendancy: "Shaman" },
      error: { status: 400, detail: "Classe Druid não existe no catálogo." },
    });
    await screen.findByRole("option", { name: "Ranger" });

    const classe = screen.getByLabelText("Classe") as HTMLSelectElement;
    const mostrado = classe.selectedOptions[0]?.textContent;
    await userEvent.click(screen.getByRole("button", { name: "Gerar Build com IA" }));

    const enviado = sentRequest(router)?.characterClass ?? "Deixar a IA escolher";
    expect(enviado).toBe(mostrado);
  });

  it("retryAt fora do formato não derruba a tela: a mensagem continua visível", async () => {
    renderWithState({
      request: { concept: "arqueiro" },
      error: { status: 429, detail: "Limite diário atingido.", retryAt: "amanhã" },
    });

    expect(await screen.findByLabelText("Conceito do personagem")).toHaveValue("arqueiro");
    expect(screen.getByRole("alert")).toHaveTextContent("Limite diário atingido.");
  });
});

describe("H2 — entradas hostis no conceito", () => {
  it("conceito só com espaços e quebras de linha é tratado como vazio", async () => {
    const router = renderWithState(null);
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.type(screen.getByLabelText("Conceito do personagem"), "   {enter}  ");
    await userEvent.click(screen.getByRole("button", { name: "Gerar Build com IA" }));

    expect(router.state.location.pathname).toBe("/criar");
    expect(screen.getByText(/Conceito do personagem é obrigatório/)).toBeInTheDocument();
  });

  it("conceito de 600 caracteres vindo da URL não é enviado acima do limite de 500", async () => {
    const longo = "a".repeat(600);
    const router = createMemoryRouter(routes, { initialEntries: [`/criar?conceito=${longo}`] });
    render(<RouterProvider router={router} />);
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.click(screen.getByRole("button", { name: "Gerar Build com IA" }));

    const enviado = sentRequest(router)?.concept as string | undefined;
    expect(enviado === undefined || enviado.length <= 500).toBe(true);
  });
});
