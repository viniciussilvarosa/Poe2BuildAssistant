import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { GUIDE_SECTIONS } from "./guideContent";

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(<RouterProvider router={router} />);
  return router;
}

describe("GuidePage", () => {
  it("mostra todas as seções, com índice apontando para cada uma", () => {
    renderAt("/guia");

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Guia rápido do Path of Exile 2");
    const indice = screen.getByRole("navigation", { name: "Seções do guia" });
    for (const s of GUIDE_SECTIONS) {
      expect(screen.getByRole("heading", { level: 2, name: s.title })).toBeInTheDocument();
      expect(within(indice).getByRole("link", { name: s.title })).toHaveAttribute("href", `#${s.id}`);
    }
  });

  it("lista as 8 classes", () => {
    renderAt("/guia");

    for (const name of ["Warrior", "Ranger", "Huntress", "Mercenary", "Monk", "Sorceress", "Witch", "Druid"]) {
      expect(screen.getByText(name, { selector: "dt" })).toBeInTheDocument();
    }
  });

  it("uma ideia abre o formulário com o conceito preenchido", async () => {
    const router = renderAt("/guia");

    await userEvent.click(screen.getByRole("link", { name: "besteiro que bombardeia grupos com granadas" }));

    expect(router.state.location.pathname).toBe("/criar");
    expect(new URLSearchParams(router.state.location.search).get("conceito")).toBe(
      "besteiro que bombardeia grupos com granadas",
    );
  });

  it("termina com a ação de montar a build", async () => {
    const router = renderAt("/guia");

    await userEvent.click(screen.getByRole("link", { name: "Montar minha build" }));

    expect(router.state.location.pathname).toBe("/criar");
    expect(new URLSearchParams(router.state.location.search).get("conceito")).toContain("energy shield");
  });

  it("o menu principal leva ao guia", async () => {
    const router = renderAt("/");

    await userEvent.click(
      within(screen.getByRole("navigation", { name: "Principal" })).getByRole("link", { name: "Guia rápido" }),
    );

    expect(router.state.location.pathname).toBe("/guia");
  });
});
