import { describe, expect, it } from "vitest";
import css from "./CreateBuildPage.module.css?raw";

/** O jsdom não calcula layout: confere a regra que impede o select de esticar (#62). */
describe("layout dos seletores", () => {
  it("cada seletor alinha o conteúdo no topo da célula da grade", () => {
    const seletor = css.match(/(?:^|\n)\.seletor\s*\{([^}]*)\}/)?.[1] ?? "";

    expect(seletor).toMatch(/align-content:\s*start/);
  });
});
