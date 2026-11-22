import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCatalogOptions, type CatalogOptions } from "../../api/catalog";
import { routes } from "../../app/routes";

vi.mock("../../api/catalog");

const OPTIONS: CatalogOptions = {
  patch: "0.5",
  classes: [{ name: "Ranger", ascendancies: [{ name: "Deadeye" }] }],
  weaponTypes: [{ name: "Bow" }, { name: "Buckler" }, { name: "Shield" }, { name: "Staff" }],
  skillGems: [
    { name: "Fireball", weaponTypes: [] },
    { name: "Ice Shot", weaponTypes: ["Bow"] },
    { name: "Lightning Arrow", weaponTypes: ["Bow"] },
    { name: "Shield Charge", weaponTypes: ["Shield"] },
  ],
  playstyles: [],
};

const names = (el: HTMLElement) =>
  within(el)
    .getAllByRole("option")
    .map((o) => o.textContent);

async function open() {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ["/criar"] })} />);
  await screen.findByRole("option", { name: "Ranger" });
}

beforeEach(() => {
  vi.mocked(getCatalogOptions).mockResolvedValue(OPTIONS);
});

describe("seletores (QA-10)", () => {
  it("escudo e broquel ficam no grupo Mão secundária, separados das armas", async () => {
    await open();

    const arma = screen.getByLabelText("Arma");
    expect(names(within(arma).getByRole("group", { name: "Armas" }))).toEqual(["Bow", "Staff"]);
    expect(names(within(arma).getByRole("group", { name: "Mão secundária" }))).toEqual(["Buckler", "Shield"]);
  });

  it("a busca filtra a habilidade principal por texto, sem ligar para maiúsculas", async () => {
    await open();

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "ARROW");

    expect(names(screen.getByLabelText("Habilidade principal"))).toEqual(["Deixar a IA escolher", "Lightning Arrow"]);
  });

  it("a busca continua respeitando o filtro da arma", async () => {
    await open();
    await userEvent.selectOptions(screen.getByLabelText("Arma"), "Shield");

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "i");

    expect(names(screen.getByLabelText("Habilidade principal"))).toEqual([
      "Deixar a IA escolher",
      "Fireball",
      "Shield Charge",
    ]);
  });

  it("a escolha atual não some da lista quando a busca não a inclui", async () => {
    await open();
    const skill = screen.getByLabelText("Habilidade principal");
    await userEvent.selectOptions(skill, "Ice Shot");

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "fire");

    expect(skill).toHaveValue("Ice Shot");
    expect(names(skill)).toEqual(["Deixar a IA escolher", "Fireball", "Ice Shot"]);
  });

  it("é usável só pelo teclado: Tab chega na busca e depois no select", async () => {
    await open();
    screen.getByLabelText("Arma").focus();

    await userEvent.tab();
    expect(screen.getByLabelText("Buscar habilidade")).toHaveFocus();
    await userEvent.keyboard("lightning");
    await userEvent.tab();
    expect(screen.getByLabelText("Habilidade principal")).toHaveFocus();
    await userEvent.selectOptions(screen.getByLabelText("Habilidade principal"), "Lightning Arrow");
    expect(screen.getByLabelText("Habilidade principal")).toHaveValue("Lightning Arrow");
  });
});

describe("busca de habilidade principal (#94, #69)", () => {
  function abrirComRouter() {
    const router = createMemoryRouter(routes, { initialEntries: ["/criar"] });
    render(<RouterProvider router={router} />);
    return router;
  }

  it("com um único resultado e nada escolhido, a habilidade fica selecionada", async () => {
    await open();

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "ice shot");

    expect(screen.getByLabelText("Habilidade principal")).toHaveValue("Ice Shot");
  });

  it("Enter escolhe o primeiro resultado e não envia o formulário", async () => {
    const router = abrirComRouter();
    await screen.findByRole("option", { name: "Ranger" });
    await userEvent.type(screen.getByLabelText(/Conceito do personagem/), "arqueiro de gelo");

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "i{Enter}");

    expect(router.state.location.pathname).toBe("/criar");
    expect(screen.getByLabelText("Habilidade principal")).toHaveValue("Fireball");
  });

  it("Enter troca a escolha atual pelo primeiro resultado da busca", async () => {
    await open();
    const skill = screen.getByLabelText("Habilidade principal");
    await userEvent.selectOptions(skill, "Ice Shot");

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "lightning{Enter}");

    expect(skill).toHaveValue("Lightning Arrow");
  });

  it("Enter sem resultado não envia o formulário nem apaga a escolha", async () => {
    const router = abrirComRouter();
    await screen.findByRole("option", { name: "Ranger" });
    await userEvent.type(screen.getByLabelText(/Conceito do personagem/), "arqueiro de gelo");
    await userEvent.selectOptions(screen.getByLabelText("Habilidade principal"), "Ice Shot");

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "frostzzzz{Enter}");

    expect(router.state.location.pathname).toBe("/criar");
    expect(screen.getByLabelText("Habilidade principal")).toHaveValue("Ice Shot");
  });

  it("palavras em qualquer ordem, sem diferença de acento ou maiúsculas", async () => {
    await open();

    await userEvent.type(screen.getByLabelText("Buscar habilidade"), "SHÓT íce");

    expect(names(screen.getByLabelText("Habilidade principal"))).toEqual(["Deixar a IA escolher", "Ice Shot"]);
  });

  it("busca sem resultado avisa, numa região aria-live ligada à busca", async () => {
    await open();
    const busca = screen.getByLabelText("Buscar habilidade");

    await userEvent.type(busca, "frostzzzz");

    const status = document.getElementById(busca.getAttribute("aria-describedby") ?? "");
    expect(status).toHaveAttribute("aria-live", "polite");
    expect(status).toHaveTextContent("Nenhuma habilidade com “frostzzzz”");
  });

  it("com resultados, a região mostra a quantidade; busca vazia não mostra mensagem", async () => {
    await open();
    const busca = screen.getByLabelText("Buscar habilidade");
    const status = () => document.getElementById(busca.getAttribute("aria-describedby") ?? "");

    expect(status()).toHaveTextContent(/^$/);
    await userEvent.type(busca, "i");
    expect(status()).toHaveTextContent("4 habilidades");
    await userEvent.clear(busca);
    expect(status()).toHaveTextContent(/^$/);
  });
});
