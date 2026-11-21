import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import css from "./HomePage.module.css?raw";

/** Ataques às promessas do redesign (card #2): foco visível, ampliação da arte, rodapé. */

/** Bloco de declarações de um seletor exato no CSS-fonte. */
function block(selector: string): string {
  const start = css.indexOf(`${selector} {`);
  expect(start, `seletor ${selector} no CSS`).toBeGreaterThanOrEqual(0);
  return css.slice(start, css.indexOf("}", start));
}

describe("HomePage — promessas do redesign", () => {
  it("L4: com foco por teclado no conceito, a moldura ganha contorno de pelo menos 2px", () => {
    const focus = block(".tooltip:focus-within");

    const outline = /outline:\s*(\d+)px\s+solid/.exec(focus);
    expect(outline, "a moldura precisa de outline sólido no foco").not.toBeNull();
    expect(Number(outline![1])).toBeGreaterThanOrEqual(2);
  });

  it("L6: a altura da arte tem teto, para nunca ampliar a fonte de 736px além de ~1,4x (≈1030px)", () => {
    const height = /height:\s*([^;]+);/.exec(block(".arte"))![1];
    const teto = /min\([^,]+,\s*(\d+)px\)/.exec(height);

    expect(teto, `altura da arte sem teto: "${height}"`).not.toBeNull();
    expect(Number(teto![1])).toBeLessThanOrEqual(1030);
  });

  it("L5: o rodapé mantém o aviso de não afiliação exato e credita a arte", () => {
    render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ["/"] })} />);

    expect(screen.getByText("Projeto independente não afiliado à Grinding Gear Games.")).toBeInTheDocument();
    expect(screen.getByText("Arte: Path of Exile 2 © Grinding Gear Games.")).toBeInTheDocument();
  });
});
