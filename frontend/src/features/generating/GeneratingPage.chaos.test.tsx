import { render, screen } from "@testing-library/react";
import { act, StrictMode } from "react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { routes } from "../../app/routes";
import { generateBuild, type ProgressEvent } from "../../api/builds";
import { getCatalogOptions } from "../../api/catalog";

vi.mock("../../api/catalog");
vi.mock("../../api/builds");

/** Ataques à tela Gerando build (card #12, PR #32). */

const STATE = { request: { concept: "arqueiro mágico" } };

function controlled() {
  let onProgress: (e: ProgressEvent) => void = () => {};
  let resolve: (b: unknown) => void = () => {};
  vi.mocked(generateBuild).mockImplementation((_r, p) => {
    onProgress = p;
    return new Promise((res) => (resolve = res as (b: unknown) => void)) as ReturnType<typeof generateBuild>;
  });
  return {
    progress: (e: unknown) => act(() => onProgress(e as ProgressEvent)),
    done: (b: unknown) => act(async () => resolve(b)),
  };
}

beforeEach(() => {
  vi.mocked(getCatalogOptions).mockResolvedValue({ patch: "0.4", classes: [], weaponTypes: [], skillGems: [], playstyles: [] });
});

describe("H3 — cota gasta uma vez por pedido", () => {
  it("StrictMode (dev) não dispara dois POSTs", () => {
    controlled();
    const router = createMemoryRouter(routes, { initialEntries: [{ pathname: "/gerando", state: STATE }] });
    render(
      <StrictMode>
        <RouterProvider router={router} />
      </StrictMode>,
    );

    expect(generateBuild).toHaveBeenCalledTimes(1);
  });

  it("sair no meio e voltar com o botão Voltar não gera uma segunda build", async () => {
    controlled();
    const router = createMemoryRouter(routes, {
      initialEntries: ["/criar", { pathname: "/gerando", state: STATE }],
      initialIndex: 1,
    });
    render(<RouterProvider router={router} />);

    await act(() => router.navigate("/"));
    await act(() => router.navigate(-1));

    expect(router.state.location.pathname).not.toBe("/");
    expect(generateBuild).toHaveBeenCalledTimes(1);
  });

  it("sair no meio: a conclusão depois não arrasta o usuário para outra página", async () => {
    const stream = controlled();
    const router = createMemoryRouter(routes, { initialEntries: [{ pathname: "/gerando", state: STATE }] });
    render(<RouterProvider router={router} />);

    await act(() => router.navigate("/"));
    await stream.done({ id: "abc" });

    expect(router.state.location.pathname).toBe("/");
  });
});

describe("H4 — contrato do stream violado", () => {
  it("etapa desconhecida (backend novo) não mostra 'Pronto' no meio da geração", () => {
    const stream = controlled();
    const router = createMemoryRouter(routes, { initialEntries: [{ pathname: "/gerando", state: STATE }] });
    render(<RouterProvider router={router} />);

    stream.progress({ step: "GENERATING", percent: 40 });
    stream.progress({ step: "RETRYING_WITH_FEEDBACK", percent: 60 });

    expect(screen.getByRole("status")).not.toHaveTextContent("Pronto");
  });

  it("done sem id não navega para /builds/undefined", async () => {
    const stream = controlled();
    const router = createMemoryRouter(routes, { initialEntries: [{ pathname: "/gerando", state: STATE }] });
    render(<RouterProvider router={router} />);

    await stream.done({});

    expect(router.state.location.pathname).not.toBe("/builds/undefined");
  });
});
