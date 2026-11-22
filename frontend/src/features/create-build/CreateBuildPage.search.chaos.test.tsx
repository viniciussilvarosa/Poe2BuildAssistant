import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCatalogOptions, type CatalogOptions } from "../../api/catalog";
import { routes } from "../../app/routes";

vi.mock("../../api/catalog");
vi.mock("../../api/builds", () => ({ generateBuild: vi.fn(() => new Promise(() => {})) }));

/** Caos do PR #95 (#94): busca da habilidade principal. Catálogo simulado, nenhuma chamada real. */

const OPTIONS: CatalogOptions = {
  patch: "0.5",
  classes: [{ name: "Ranger", ascendancies: [{ name: "Deadeye" }] }],
  weaponTypes: [{ name: "Bow" }, { name: "Shield" }],
  skillGems: [
    { name: "Alchemist's Boon", weaponTypes: [] },
    { name: "Fireball", weaponTypes: [] },
    { name: "Ice Shot", weaponTypes: ["Bow"] },
    { name: "Shield Charge", weaponTypes: ["Shield"] },
  ],
  playstyles: [],
};

const busca = () => screen.getByLabelText("Buscar habilidade");
const skill = () => screen.getByLabelText("Habilidade principal");
const status = () => document.getElementById(busca().getAttribute("aria-describedby") ?? "");

async function abrir() {
  const router = createMemoryRouter(routes, { initialEntries: ["/criar"] });
  render(<RouterProvider router={router} />);
  await screen.findByRole("option", { name: "Ranger" });
  return router;
}

beforeEach(() => {
  vi.mocked(getCatalogOptions).mockResolvedValue(OPTIONS);
});

describe("K1: Enter com a busca vazia", () => {
  // CHAOS: KILLED no PR #95. Busca vazia "combina" com todas as habilidades, e o Enter escolhia a
  // primeira do catálogo (Alchemist's Boon) sem o usuário ter buscado nada.
  it("não escolhe nenhuma habilidade sozinho", async () => {
    await abrir();

    await userEvent.type(busca(), "{Enter}");

    expect(skill()).toHaveValue("");
  });

  it("só espaços também conta como busca vazia", async () => {
    await abrir();

    await userEvent.type(busca(), "   {Enter}");

    expect(skill()).toHaveValue("");
  });
});

describe("K2: apóstrofo curvo do teclado do celular", () => {
  // CHAOS: KILLED no PR #95. iOS e muitos teclados trocam ' por ’ ("alchemist’s"), e a busca não
  // achava "Alchemist's Boon".
  it("“alchemist’s” acha Alchemist's Boon", async () => {
    await abrir();

    await userEvent.type(busca(), "alchemist’s");

    expect(status()).toHaveTextContent("1 habilidade");
    expect(skill()).toHaveValue("Alchemist's Boon");
  });
});

describe("K3: texto hostil na busca", () => {
  it("HTML no termo aparece como texto no aviso, sem virar elemento", async () => {
    await abrir();

    await userEvent.type(busca(), "<img src=x onerror=alert(1)>");

    expect(status()).toHaveTextContent("Nenhuma habilidade com “<img src=x onerror=alert(1)>”");
    expect(status()?.querySelector("img")).toBeNull();
  });
});

describe("K4: busca antes de o catálogo carregar", () => {
  // CHAOS: KILLED no PR #95. Com o catálogo ainda carregando, o aviso dizia "Nenhuma habilidade com “ice”".
  it("não diz 'Nenhuma habilidade' enquanto as opções ainda estão carregando", async () => {
    vi.mocked(getCatalogOptions).mockReturnValue(new Promise(() => {}));
    render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ["/criar"] })} />);

    await userEvent.type(await screen.findByLabelText("Buscar habilidade"), "ice");

    expect(status()).not.toHaveTextContent(/Nenhuma habilidade/);
  });
});

describe("K5: Enter que confirma uma composição (IME)", () => {
  // CHAOS: KILLED no PR #95. O Enter que fecha a composição (japonês, chinês) escolhia o primeiro resultado.
  it("Enter durante a composição de texto não escolhe habilidade", async () => {
    await abrir();
    fireEvent.change(busca(), { target: { value: "i" } });
    expect(skill()).toHaveValue("");

    fireEvent.keyDown(busca(), { key: "Enter", isComposing: true, keyCode: 229 });

    expect(skill()).toHaveValue("");
  });
});

describe("K7: escolha automática e depois troca de arma", () => {
  it("a habilidade escolhida pela busca sai quando a arma nova não a aceita", async () => {
    await abrir();
    await userEvent.type(busca(), "ice shot");
    expect(skill()).toHaveValue("Ice Shot");

    await userEvent.selectOptions(screen.getByLabelText("Arma"), "Shield");

    expect(skill()).toHaveValue("");
  });
});

describe("K6: termo longo sem espaço", () => {
  // CHAOS: KILLED no PR #95. O aviso repete o termo; ~60 caracteres sem espaço já passavam da largura
  // da coluna e 2000 deixavam a página com 12230 px de largura num celular de 375 px. O jsdom não
  // calcula layout: confere a regra que quebra a palavra (no navegador, a página volta a 375 px).
  it("o aviso da busca quebra palavras longas em vez de alargar a página", async () => {
    const { default: css } = await import("./CreateBuildPage.module.css?raw");
    const regra = css.match(/(?:^|\n)\.buscaStatus\s*\{([^}]*)\}/)?.[1] ?? "";

    expect(regra).toMatch(/overflow-wrap:\s*anywhere/);
  });
});
