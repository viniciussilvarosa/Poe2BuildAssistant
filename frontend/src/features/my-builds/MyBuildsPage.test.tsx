import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { getBuild } from "../../api/builds";
import { ApiError } from "../../api/client";
import { getMe, getMyBuilds, type MyBuild } from "../../api/me";

vi.mock("../../api/me");
vi.mock("../../api/builds");

const BUILDS: MyBuild[] = [
  {
    id: "00000000-0000-0000-0000-00000000000b",
    createdAt: "2026-11-21T12:00:00Z",
    concept: "arqueiro que congela tudo e nunca para de se mover pelo mapa inteiro da campanha",
    characterClass: "Ranger",
    ascendancy: "Deadeye",
    mainSkill: "Ice Shot",
  },
  {
    id: "00000000-0000-0000-0000-00000000000a",
    createdAt: "2026-11-20T12:00:00Z",
    concept: "bruxa que invoca mortos",
    characterClass: "Witch",
    ascendancy: "Infernalist",
    mainSkill: "Raise Zombie",
  },
];

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

beforeEach(() => {
  vi.mocked(getMe).mockResolvedValue({ name: "Ana", role: "USER" });
  vi.mocked(getMyBuilds).mockResolvedValue(BUILDS);
  vi.mocked(getBuild).mockReturnValue(new Promise(() => {}));
});

describe("MyBuildsPage", () => {
  it("lista builds do usuário com classe e skill", async () => {
    renderAt("/minhas-builds");

    const itens = await screen.findAllByRole("listitem");
    expect(itens).toHaveLength(2);
    expect(itens[0]).toHaveTextContent("Ice Shot");
    expect(itens[0]).toHaveTextContent("Ranger");
  });

  it("clicar numa build navega para /builds/:id", async () => {
    const router = renderAt("/minhas-builds");

    await userEvent.click(await screen.findByRole("link", { name: /Ice Shot/ }));

    expect(router.state.location.pathname).toBe("/builds/00000000-0000-0000-0000-00000000000b");
  });

  it("lista vazia mostra convite para criar a primeira build", async () => {
    vi.mocked(getMyBuilds).mockResolvedValue([]);
    renderAt("/minhas-builds");

    expect(await screen.findByText(/Você ainda não gerou nenhuma build/)).toBeInTheDocument();
    expect(within(screen.getByRole("main")).getByRole("link", { name: "Criar Build" })).toHaveAttribute("href", "/criar");
  });

  it("visitante vê convite para entrar", async () => {
    vi.mocked(getMe).mockResolvedValue(null);
    vi.mocked(getMyBuilds).mockRejectedValue(new ApiError(401, "Entre com o Google para ver esta página."));
    renderAt("/minhas-builds");

    expect(await within(screen.getByRole("main")).findByRole("link", { name: "Entrar com Google" })).toHaveAttribute(
      "href",
      "/oauth2/authorization/google",
    );
  });

  it("conceito longo aparece resumido, inteiro só no title", async () => {
    renderAt("/minhas-builds");

    const [primeiro] = await screen.findAllByRole("listitem");
    expect(primeiro).not.toHaveTextContent(BUILDS[0].concept);
    expect(within(primeiro).getByTitle(BUILDS[0].concept)).toBeInTheDocument();
  });
});
