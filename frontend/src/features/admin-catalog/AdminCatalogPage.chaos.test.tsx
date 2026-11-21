import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import {
  createDraft,
  getCurrentDraft,
  getDraft,
  importPassiveTree,
  publishDraft,
  removeItem,
  saveItem,
  type DraftCatalog,
} from "../../api/adminCatalog";
import { getCatalogOptions } from "../../api/catalog";
import { ApiError } from "../../api/client";
import { getMe } from "../../api/me";

vi.mock("../../api/me");
vi.mock("../../api/catalog");
vi.mock("../../api/adminCatalog");

/** Ataques à tela de administração do catálogo (card #18, PR #39). */

const DRAFT: DraftCatalog = {
  version: { id: 9, patch: "1.0", status: "DRAFT" },
  classes: [{ name: "Ranger", ascendancies: ["Deadeye"] }],
  weaponTypes: ["Bow"],
  skillGems: [{ name: "Ice Shot", weaponTypes: ["Bow"] }],
  supportGems: ["Pierce I"],
  playstyles: ['<img src=x onerror="alert(1)">'],
  notables: 983,
};

/** Promessa que o teste resolve quando quiser: simula a API ainda respondendo. */
function pending<T>() {
  let resolve: (value: T) => void = () => {};
  const promise = new Promise<T>((r) => (resolve = r));
  return { promise, resolve };
}

function renderAdmin() {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ["/admin"] })} />);
}

beforeEach(() => {
  vi.mocked(getMe).mockResolvedValue({ name: "Curador", role: "ADMIN" });
  vi.mocked(getCatalogOptions).mockResolvedValue({ patch: "0.5", classes: [], weaponTypes: [], skillGems: [], playstyles: [] });
  vi.mocked(getCurrentDraft).mockResolvedValue(DRAFT.version);
  vi.mocked(getDraft).mockResolvedValue(DRAFT);
  vi.mocked(saveItem).mockResolvedValue(undefined);
  vi.mocked(removeItem).mockResolvedValue(undefined);
});

describe("H1 — cliques repetidos em ações caras ou irreversíveis", () => {
  it("duplo clique em Confirmar publicação publica uma vez só", async () => {
    const resposta = pending<{ id: number; patch: string; status: "PUBLISHED" }>();
    vi.mocked(publishDraft).mockReturnValue(resposta.promise);
    renderAdmin();

    await userEvent.click(await screen.findByRole("button", { name: "Publicar 1.0" }));
    await userEvent.dblClick(screen.getByRole("button", { name: "Confirmar publicação" }));

    expect(publishDraft).toHaveBeenCalledTimes(1);
  });

  it("duplo clique em Importar árvore baixa o export da GGG uma vez só", async () => {
    const resposta = pending<{ notables: number }>();
    vi.mocked(importPassiveTree).mockReturnValue(resposta.promise);
    renderAdmin();

    await userEvent.dblClick(await screen.findByRole("button", { name: "Importar árvore de passivas" }));

    expect(importPassiveTree).toHaveBeenCalledTimes(1);
  });

  it("duplo clique em Criar rascunho cria uma vez só (o segundo seria 409)", async () => {
    vi.mocked(getCurrentDraft).mockResolvedValue(null);
    const resposta = pending<typeof DRAFT.version>();
    vi.mocked(createDraft).mockReturnValue(resposta.promise);
    renderAdmin();

    await userEvent.type(await screen.findByLabelText("Patch do rascunho"), "1.0");
    await userEvent.dblClick(screen.getByRole("button", { name: "Criar rascunho" }));

    expect(createDraft).toHaveBeenCalledTimes(1);
  });
});

describe("H2 — editar não pode virar duplicar", () => {
  it("trocar o nome ao editar não deixa o item antigo para trás", async () => {
    renderAdmin();

    await userEvent.click(await screen.findByRole("tab", { name: "Gemas" }));
    await userEvent.click(screen.getByRole("button", { name: "Editar" }));
    const nome = screen.getByLabelText("Nome") as HTMLInputElement;
    // Um campo travado na edição não aceita o novo nome; um editável recebe "Ice Shot II".
    if (!nome.readOnly) {
      await userEvent.clear(nome);
      await userEvent.type(nome, "Ice Shot II");
    }
    await userEvent.click(screen.getByRole("button", { name: "Salvar" }));

    const renomeou = vi.mocked(saveItem).mock.calls.some(([, , body]) => body.name === "Ice Shot II");
    const removeuAntigo = vi.mocked(removeItem).mock.calls.some(([, , name]) => name === "Ice Shot");
    expect(!renomeou || removeuAntigo).toBe(true);
  });
});

describe("H3 — entradas e respostas hostis", () => {
  it("nome com HTML aparece como texto na lista", async () => {
    renderAdmin();

    await userEvent.click(await screen.findByRole("tab", { name: "Estilos" }));

    expect(within(screen.getByRole("tabpanel")).getByText('<img src=x onerror="alert(1)">')).toBeInTheDocument();
    expect(document.querySelector("img[src=x]")).toBeNull();
  });

  it("patch vazio mostra o erro da API em pt-BR, sem travar a tela", async () => {
    vi.mocked(getCurrentDraft).mockResolvedValue(null);
    vi.mocked(createDraft).mockRejectedValue(new ApiError(400, "Mudança recusada: versão do catálogo sem nome."));
    renderAdmin();

    await userEvent.click(await screen.findByRole("button", { name: "Criar rascunho" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("versão do catálogo sem nome");
    expect(screen.getByRole("button", { name: "Criar rascunho" })).toBeInTheDocument();
  });
});
