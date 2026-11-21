import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { getCatalogOptions, type CatalogOptions } from "../../api/catalog";

vi.mock("../../api/catalog");

const OPTIONS: CatalogOptions = {
  patch: "0.4",
  classes: [
    { name: "Ranger", ascendancies: [{ name: "Deadeye" }] },
    { name: "Witch", ascendancies: [{ name: "Infernalist" }] },
  ],
  weaponTypes: [{ name: "Bow" }, { name: "Staff" }],
  skillGems: [
    { name: "Fireball", weaponTypes: [] },
    { name: "Ice Shot", weaponTypes: ["Bow"] },
  ],
  playstyles: [{ name: "À distância" }],
};

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

function optionsOf(label: string) {
  return within(screen.getByLabelText(label))
    .getAllByRole("option")
    .map((o) => o.textContent);
}

beforeEach(() => {
  vi.mocked(getCatalogOptions).mockResolvedValue(OPTIONS);
});

describe("CreateBuildPage", () => {
  it("bloqueia o envio com conceito vazio e indica o campo obrigatório", async () => {
    const router = renderAt("/criar");
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.click(screen.getByRole("button", { name: "Gerar Build com IA" }));

    expect(screen.getByText(/Conceito do personagem é obrigatório/)).toBeInTheDocument();
    expect(screen.getByLabelText("Conceito do personagem")).toHaveAttribute("aria-invalid", "true");
    expect(router.state.location.pathname).toBe("/criar");
  });

  it("ascendências mostradas são só as da classe escolhida", async () => {
    renderAt("/criar");
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.selectOptions(screen.getByLabelText("Classe"), "Ranger");

    expect(optionsOf("Ascendência")).toEqual(["Deixar a IA escolher", "Deadeye"]);
  });

  it("trocar a classe limpa a ascendência incompatível", async () => {
    renderAt("/criar");
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.selectOptions(screen.getByLabelText("Classe"), "Ranger");
    await userEvent.selectOptions(screen.getByLabelText("Ascendência"), "Deadeye");
    await userEvent.selectOptions(screen.getByLabelText("Classe"), "Witch");

    expect(screen.getByLabelText("Ascendência")).toHaveValue("");
    expect(within(screen.getByLabelText("Ascendência")).getByRole("option", { selected: true })).toHaveTextContent(
      "Deixar a IA escolher",
    );
  });

  it("escolher a ascendência primeiro define a classe dela", async () => {
    renderAt("/criar");
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.selectOptions(screen.getByLabelText("Ascendência"), "Infernalist");

    expect(screen.getByLabelText("Classe")).toHaveValue("Witch");
  });

  it("habilidade incompatível com a arma escolhida some da lista", async () => {
    renderAt("/criar");
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.selectOptions(screen.getByLabelText("Habilidade principal"), "Ice Shot");
    await userEvent.selectOptions(screen.getByLabelText("Arma"), "Staff");

    expect(optionsOf("Habilidade principal")).toEqual(["Deixar a IA escolher", "Fireball"]);
    expect(screen.getByLabelText("Habilidade principal")).toHaveValue("");
  });

  it("envia só os seletores preenchidos", async () => {
    const router = renderAt("/criar");
    await screen.findByRole("option", { name: "Ranger" });

    await userEvent.type(screen.getByLabelText("Conceito do personagem"), "arqueiro mágico");
    await userEvent.selectOptions(screen.getByLabelText("Classe"), "Ranger");
    await userEvent.selectOptions(screen.getByLabelText("Arma"), "Bow");
    await userEvent.click(screen.getByRole("button", { name: "Gerar Build com IA" }));

    expect(router.state.location.pathname).toBe("/gerando");
    expect(router.state.location.state).toEqual({
      request: { concept: "arqueiro mágico", characterClass: "Ranger", weaponType: "Bow" },
    });
  });

  it("traz o conceito digitado na tela inicial", async () => {
    renderAt("/criar?conceito=arqueiro%20de%20gelo");

    expect(await screen.findByLabelText("Conceito do personagem")).toHaveValue("arqueiro de gelo");
  });

  it("sem catálogo publicado avisa e ainda deixa a IA escolher tudo", async () => {
    vi.mocked(getCatalogOptions).mockRejectedValue(
      Object.assign(new Error("x"), { status: 503, detail: "O catálogo do jogo ainda não foi publicado." }),
    );
    renderAt("/criar");

    expect(await screen.findByRole("alert")).toHaveTextContent("O catálogo do jogo ainda não foi publicado.");
    expect(optionsOf("Classe")).toEqual(["Deixar a IA escolher"]);
  });

  it("volta de um erro com o formulário preenchido, a mensagem e o horário no fuso do navegador", async () => {
    const router = createMemoryRouter(routes, {
      initialEntries: [
        {
          pathname: "/criar",
          state: {
            request: { concept: "arqueiro mágico", characterClass: "Ranger" },
            error: { status: 429, detail: "Você atingiu o limite diário de builds.", retryAt: "2026-11-22T00:00:00Z" },
          },
        },
      ],
    });
    render(<RouterProvider router={router} />);
    const horario = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(
      new Date("2026-11-22T00:00:00Z"),
    );

    expect(screen.getByLabelText("Conceito do personagem")).toHaveValue("arqueiro mágico");
    const alerta = screen.getByRole("alert");
    expect(alerta).toHaveTextContent("Você atingiu o limite diário de builds.");
    expect(alerta).toHaveTextContent(horario);
    expect(screen.getByRole("link", { name: "Entrar com Google" })).toHaveAttribute("href", "/oauth2/authorization/google");
    await screen.findByRole("option", { name: "Ranger" });
    expect(screen.getByLabelText("Classe")).toHaveValue("Ranger");
  });
});
