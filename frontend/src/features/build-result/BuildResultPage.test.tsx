import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { getMyRating, getBuild } from "../../api/builds";
import { ApiError } from "../../api/client";
import { buildFixture } from "./fixtures";

vi.mock("../../api/builds");
// O voto atual é lido ao abrir a build (#50); nestes testes ninguém votou ainda.
beforeEach(() => {
  vi.mocked(getMyRating).mockResolvedValue(null);
});


const ID = "00000000-0000-0000-0000-000000000042";

function renderFromGeneration(build = buildFixture()) {
  const router = createMemoryRouter(routes, { initialEntries: [{ pathname: `/builds/${build.id}`, state: { build } }] });
  render(<RouterProvider router={router} />);
  return router;
}

function stubClipboard(writeText: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
}

describe("BuildResultPage", () => {
  it("exibe os cinco cartões com o conteúdo da build", () => {
    renderFromGeneration();

    for (const name of ["Resumo & Sinergia", "Gemas", "Árvore de Passivas", "Prioridades de Equipamento", "Dicas de campanha"]) {
      expect(screen.getByRole("heading", { name })).toBeInTheDocument();
    }
    expect(screen.getAllByText("Ice Shot").length).toBeGreaterThan(0);
    expect(screen.getByText("Pierce")).toBeInTheDocument();
    expect(screen.getByText("Frozen Edge")).toBeInTheDocument();
    expect(getBuild).not.toHaveBeenCalled();
  });

  it("mostra a justificativa quando a IA escolheu a classe", () => {
    renderFromGeneration(
      buildFixture({ choiceReasons: [{ field: "characterClass", reason: "A Ranger domina arcos desde o início." }] }),
    );

    expect(screen.getByText("A Ranger domina arcos desde o início.")).toBeInTheDocument();
    expect(screen.getByText("Classe")).toBeInTheDocument();
  });

  it("Copiar Resumo copia texto com classe, skill e suportes", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);
    renderFromGeneration();

    await userEvent.click(screen.getByRole("button", { name: "Copiar Resumo" }));

    expect(writeText).toHaveBeenCalledWith(expect.stringContaining("Ice Shot"));
    const texto = writeText.mock.calls[0][0] as string;
    expect(texto).toContain("Ranger");
    expect(texto).toContain("Pierce");
    expect(await screen.findByText("Resumo copiado")).toBeInTheDocument();
  });

  it("abre por link direto buscando a build na API", async () => {
    vi.mocked(getBuild).mockResolvedValue(buildFixture());
    const router = createMemoryRouter(routes, { initialEntries: [`/builds/${ID}`] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByRole("heading", { name: "Gemas" })).toBeInTheDocument();
    expect(getBuild).toHaveBeenCalledWith(ID);
  });

  it("id inexistente mostra mensagem amigável e botão Criar nova build", async () => {
    vi.mocked(getBuild).mockRejectedValue(new ApiError(404, "Build não encontrada."));
    const router = createMemoryRouter(routes, { initialEntries: ["/builds/nao-existe"] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByRole("heading", { name: "Build não encontrada" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Criar nova build" })).toHaveAttribute("href", "/criar");
  });

  it("falha do clipboard mostra o texto para cópia manual", async () => {
    stubClipboard(vi.fn().mockRejectedValue(new Error("NotAllowedError")));
    renderFromGeneration();

    await userEvent.click(screen.getByRole("button", { name: "Copiar Resumo" }));

    const manual = await screen.findByLabelText(/Copie o resumo/);
    expect((manual as HTMLTextAreaElement).value).toContain("Ice Shot");
  });

  it("texto da IA com HTML aparece como texto, nunca como marcação", () => {
    renderFromGeneration(buildFixture({ summary: '<img src=x onerror="alert(1)">Resumo' }));

    expect(screen.getByText('<img src=x onerror="alert(1)">Resumo')).toBeInTheDocument();
    expect(document.querySelector("img[src=x]")).toBeNull();
  });
});
