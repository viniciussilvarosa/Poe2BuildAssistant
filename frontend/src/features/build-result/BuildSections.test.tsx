import { render, screen } from "@testing-library/react";
import { SummarySection } from "./BuildSections";
import { buildFixture } from "./fixtures";

function renderReasons(fields: string[]) {
  const build = buildFixture({ choiceReasons: fields.map((field, i) => ({ field, reason: `motivo ${i}` })) }).build;
  render(<SummarySection build={build} />);
  return [...document.querySelectorAll("dt")].map((dt) => dt.textContent);
}

describe("SummarySection — rótulos das justificativas da IA (bug #49)", () => {
  it("suportes, notables e equipamento aparecem em pt-BR", () => {
    expect(renderReasons(["supports", "supportGems", "passiveTree.notables", "notables", "passiveTree", "gear", "gear.weapon"])).toEqual([
      "Gemas de suporte",
      "Gemas de suporte",
      "Notables",
      "Notables",
      "Árvore de passivas",
      "Equipamento",
      "Equipamento",
    ]);
  });

  it("campo desconhecido mostra Escolha da IA, nunca o nome técnico", () => {
    expect(renderReasons(["someInternalField", "campaignTips.0"])).toEqual(["Escolha da IA", "Dicas de campanha"]);
    expect(screen.queryByText("someInternalField")).toBeNull();
  });

  it("os rótulos que já existiam continuam iguais", () => {
    expect(renderReasons(["characterClass", "ascendancy", "weaponType", "mainSkill", "playstyle"])).toEqual([
      "Classe",
      "Ascendência",
      "Arma",
      "Habilidade principal",
      "Estilo de jogo",
    ]);
  });
});
