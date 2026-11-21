import { formatBuildSummary } from "./formatBuildSummary";
import { buildFixture } from "./fixtures";

describe("formatBuildSummary", () => {
  it("resume a build em texto simples, em pt-BR, com nomes de jogo em inglês", () => {
    const texto = formatBuildSummary(buildFixture());

    expect(texto).toContain("Ice Shot");
    expect(texto).toContain("Ranger (Deadeye)");
    expect(texto).toContain("Suportes: Pierce");
    expect(texto).toContain("Notables: Swift Flight, Cold Precision, Frozen Edge, Agile Feet, Iron Skin");
    expect(texto).toContain("Pegue a Ascendência no Ato 2.");
    expect(texto).toContain("arqueiro mágico");
  });

  it("listas vazias não deixam linhas penduradas", () => {
    const texto = formatBuildSummary(buildFixture({ campaignTips: [], gear: { weapon: [], armour: [], accessories: [] } }));

    expect(texto).not.toMatch(/Dicas de campanha:\s*$/m);
    expect(texto).not.toContain("Armadura:");
  });
});
