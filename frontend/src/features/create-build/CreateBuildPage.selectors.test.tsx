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
