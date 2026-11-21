import { render, screen, within } from "@testing-library/react";
import { act } from "react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { ApiError } from "../../api/client";
import { generateBuild, type BuildRequest, type ProgressEvent } from "../../api/builds";
import { getCatalogOptions } from "../../api/catalog";

vi.mock("../../api/catalog");
vi.mock("../../api/builds");

const REQUEST: BuildRequest = { concept: "arqueiro mágico", characterClass: "Ranger" };

/** Controla o stream: o teste decide quando cada evento chega. */
function controlledStream() {
  let onProgress: (e: ProgressEvent) => void = () => {};
  let resolve: (b: { id: string }) => void = () => {};
  let reject: (e: unknown) => void = () => {};
  vi.mocked(generateBuild).mockImplementation((_req, progress) => {
    onProgress = progress;
    return new Promise((res, rej) => {
      resolve = res as typeof resolve;
      reject = rej;
    });
  });
  return {
    progress: (e: ProgressEvent) => act(() => onProgress(e)),
    done: (id: string) => act(async () => resolve({ id })),
    fail: (e: unknown) => act(async () => reject(e)),
  };
}

function renderGenerating() {
  const router = createMemoryRouter(routes, { initialEntries: [{ pathname: "/gerando", state: { request: REQUEST } }] });
  render(<RouterProvider router={router} />);
  return router;
}

beforeEach(() => {
  vi.mocked(getCatalogOptions).mockResolvedValue({
    patch: "0.4",
    classes: [{ name: "Ranger", ascendancies: [] }],
    weaponTypes: [],
    skillGems: [],
    playstyles: [],
  });
});

describe("GeneratingPage", () => {
  it("atualiza percentual e checklist a cada evento progress", async () => {
    const stream = controlledStream();
    renderGenerating();

    stream.progress({ step: "VALIDATING_INPUT", percent: 10 });
    expect(screen.getByText("10%")).toBeInTheDocument();
    stream.progress({ step: "GENERATING", percent: 40 });

    expect(screen.getByText("40%")).toBeInTheDocument();
    const etapas = within(screen.getByRole("list"));
    expect(etapas.getByText("Gerando build com IA")).toHaveAttribute("aria-current", "step");
    expect(etapas.getByText("Validando suas escolhas")).toHaveAttribute("data-estado", "feita");
  });

  it("anuncia o progresso a leitores de tela", () => {
    controlledStream();
    renderGenerating();

    expect(screen.getByRole("status")).toHaveAttribute("aria-live", "polite");
  });

  it("evento done navega para o resultado com a build", async () => {
    const stream = controlledStream();
    const router = renderGenerating();

    await stream.done("00000000-0000-0000-0000-000000000042");

    expect(router.state.location.pathname).toBe("/builds/00000000-0000-0000-0000-000000000042");
  });

  it("erro 429 volta ao formulário preenchido com mensagem e horário", async () => {
    const stream = controlledStream();
    const router = renderGenerating();

    await stream.fail(new ApiError(429, "Você atingiu o limite diário de builds.", "2026-11-22T00:00:00Z"));

    expect(router.state.location.pathname).toBe("/criar");
    expect(await screen.findByLabelText("Conceito do personagem")).toHaveValue("arqueiro mágico");
    const horario = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(
      new Date("2026-11-22T00:00:00Z"),
    );
    const alerta = screen.getByRole("alert");
    expect(alerta).toHaveTextContent("Você atingiu o limite diário de builds.");
    expect(alerta).toHaveTextContent(horario);
    expect(screen.getByRole("link", { name: "Entrar com Google" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google",
    );
    expect(await screen.findByLabelText("Classe")).toHaveValue("Ranger");
  });

  it("acesso direto sem pedido volta ao formulário sem gerar nada", () => {
    controlledStream();
    const router = createMemoryRouter(routes, { initialEntries: ["/gerando"] });
    render(<RouterProvider router={router} />);

    expect(router.state.location.pathname).toBe("/criar");
    expect(generateBuild).not.toHaveBeenCalled();
  });
});
