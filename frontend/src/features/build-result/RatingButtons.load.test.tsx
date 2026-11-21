import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { getMyRating, rateBuild } from "../../api/builds";
import { RatingButtons } from "./RatingButtons";

vi.mock("../../api/builds");

describe("RatingButtons — voto já registrado (bug #50)", () => {
  it("ao abrir a build, marca o voto que o servidor já tem", async () => {
    vi.mocked(getMyRating).mockResolvedValue("DOWN");
    render(<RatingButtons buildId="X" />);

    expect(await screen.findByRole("button", { name: /Não ajudou/, pressed: true })).toBeInTheDocument();
    expect(getMyRating).toHaveBeenCalledWith("X");
  });

  it("sem voto registrado, nada vem marcado", async () => {
    vi.mocked(getMyRating).mockResolvedValue(null);
    render(<RatingButtons buildId="X" />);
    await act(async () => {});

    expect(screen.getByRole("button", { name: /^👍/ })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: /Não ajudou/ })).toHaveAttribute("aria-pressed", "false");
  });

  it("um clique antes de a leitura voltar não é sobrescrito por ela", async () => {
    let responder: (v: "UP" | "DOWN" | null) => void = () => {};
    vi.mocked(getMyRating).mockReturnValue(new Promise((r) => (responder = r)));
    vi.mocked(rateBuild).mockResolvedValue(undefined);
    render(<RatingButtons buildId="X" />);

    await userEvent.click(screen.getByRole("button", { name: /^👍/ }));
    await act(async () => responder("DOWN"));

    expect(screen.getByRole("button", { name: /^👍/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("falha na leitura não mostra erro: os botões só ficam desmarcados", async () => {
    vi.mocked(getMyRating).mockRejectedValue(new Error("rede"));
    render(<RatingButtons buildId="X" />);
    await act(async () => {});

    expect(screen.queryByRole("alert")).toBeNull();
  });
});
