import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import {
  createDraft,
  getCurrentDraft,
  getDraft,
  publishDraft,
  saveItem,
  type DraftCatalog,
} from "../../api/adminCatalog";
import { getCatalogOptions } from "../../api/catalog";
import { ApiError } from "../../api/client";
import { getMe } from "../../api/me";

vi.mock("../../api/me");
vi.mock("../../api/catalog");
vi.mock("../../api/adminCatalog");

const DRAFT: DraftCatalog = {
  version: { id: 9, patch: "1.0", status: "DRAFT" },
  classes: [{ name: "Ranger", ascendancies: ["Deadeye"] }],
  weaponTypes: ["Bow", "Staff"],
  skillGems: [{ name: "Ice Shot", weaponTypes: ["Bow"] }],
  supportGems: ["Pierce I"],
  playstyles: ["À distância"],
  notables: 983,
};

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

beforeEach(() => {
  vi.mocked(getMe).mockResolvedValue({ name: "Curador", role: "ADMIN" });
  vi.mocked(getCatalogOptions).mockResolvedValue({ patch: "0.5", classes: [], weaponTypes: [], skillGems: [], playstyles: [] });
  vi.mocked(getCurrentDraft).mockResolvedValue(null);
  vi.mocked(getDraft).mockResolvedValue(DRAFT);
  vi.mocked(saveItem).mockResolvedValue(undefined);
});

describe("AdminCatalogPage", () => {
  it("usuário sem ADMIN não vê o menu Admin e /admin mostra Acesso restrito", async () => {
    vi.mocked(getMe).mockResolvedValue({ name: "Ana", role: "USER" });
    renderAt("/admin");

    expect(await screen.findByRole("heading", { name: "Acesso restrito" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Admin" })).toBeNull();
    expect(getCurrentDraft).not.toHaveBeenCalled();
  });

  it("ADMIN vê o link Admin no menu", async () => {
    renderAt("/");

    expect(await screen.findByRole("link", { name: "Admin" })).toHaveAttribute("href", "/admin");
  });

  it("sem rascunho mostra versão publicada e botão Criar rascunho", async () => {
    renderAt("/admin");

    expect(await screen.findByText(/Versão publicada: 0\.5/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Criar rascunho" })).toBeInTheDocument();
  });

  it("erro 409 de rascunho existente mostra mensagem amigável", async () => {
    vi.mocked(createDraft).mockRejectedValue(new ApiError(409, "Já existe um rascunho do catálogo (versão 1.0)."));
    renderAt("/admin");

    await userEvent.type(await screen.findByLabelText("Patch do rascunho"), "1.0");
    await userEvent.click(screen.getByRole("button", { name: "Criar rascunho" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Já existe um rascunho do catálogo (versão 1.0).");
  });

  it("adicionar gema ao rascunho chama a API e aparece na lista", async () => {
    vi.mocked(getCurrentDraft).mockResolvedValue(DRAFT.version);
    vi.mocked(getDraft)
      .mockResolvedValueOnce(DRAFT)
      .mockResolvedValue({ ...DRAFT, skillGems: [...DRAFT.skillGems, { name: "Nova Gem", weaponTypes: ["Bow"] }] });
    renderAt("/admin");

    await userEvent.click(await screen.findByRole("tab", { name: "Gemas" }));
    await userEvent.type(screen.getByLabelText("Nome"), "Nova Gem");
    await userEvent.click(screen.getByRole("checkbox", { name: "Bow" }));
    await userEvent.click(screen.getByRole("button", { name: "Salvar" }));

    expect(saveItem).toHaveBeenCalledWith(9, "skill-gems", { name: "Nova Gem", weaponTypes: ["Bow"] });
    expect(await within(screen.getByRole("tabpanel")).findByText("Nova Gem")).toBeInTheDocument();
  });

  it("ascendência exige selecionar a classe", async () => {
    vi.mocked(getCurrentDraft).mockResolvedValue(DRAFT.version);
    renderAt("/admin");

    await userEvent.click(await screen.findByRole("tab", { name: "Ascendências" }));
    await userEvent.type(screen.getByLabelText("Nome"), "Nova Ascendência");
    await userEvent.click(screen.getByRole("button", { name: "Salvar" }));

    expect(screen.getByText("Escolha a classe da ascendência.")).toBeInTheDocument();
    expect(saveItem).not.toHaveBeenCalled();
  });

  it("publicar pede confirmação e mostra sucesso", async () => {
    vi.mocked(getCurrentDraft).mockResolvedValue(DRAFT.version);
    vi.mocked(publishDraft).mockResolvedValue({ id: 9, patch: "1.0", status: "PUBLISHED" });
    renderAt("/admin");

    await userEvent.click(await screen.findByRole("button", { name: "Publicar 1.0" }));
    expect(publishDraft).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "Confirmar publicação" }));

    expect(publishDraft).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("status")).toHaveTextContent("Versão 1.0 publicada");
  });

  it("busca filtra a lista da aba", async () => {
    vi.mocked(getCurrentDraft).mockResolvedValue(DRAFT.version);
    renderAt("/admin");

    await userEvent.click(await screen.findByRole("tab", { name: "Armas" }));
    await userEvent.type(screen.getByLabelText("Buscar"), "sta");

    const painel = within(screen.getByRole("tabpanel"));
    expect(painel.getByText("Staff")).toBeInTheDocument();
    expect(painel.queryByText("Bow")).toBeNull();
  });
});
