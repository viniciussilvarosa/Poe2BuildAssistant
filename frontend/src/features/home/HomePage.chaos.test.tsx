import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";

/** Ataques à tela inicial (card #2, PR da tela): conceito hostil na URL de /criar. */
describe("HomePage — conceito hostil", () => {
  it.each([
    "arqueiro&classe=Witch",
    "monge#ancora",
    "bruxa?admin=true",
    "a=b&c=d",
    "100% gelo + raios",
  ])("F11: %s vai inteiro em ?conceito= sem injetar outros parâmetros", async (concept) => {
    const router = createMemoryRouter(routes, { initialEntries: ["/"] });
    render(<RouterProvider router={router} />);

    await userEvent.type(screen.getByLabelText("Que personagem você quer jogar?"), concept);
    await userEvent.click(screen.getByRole("link", { name: "Criar Build" }));

    const params = new URLSearchParams(router.state.location.search);
    expect(router.state.location.pathname).toBe("/criar");
    expect([...params.keys()]).toEqual(["conceito"]);
    expect(params.get("conceito")).toBe(concept);
    expect(router.state.location.hash).toBe("");
  });
});
