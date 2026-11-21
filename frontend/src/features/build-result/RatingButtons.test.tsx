import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { getMyRating, rateBuild } from "../../api/builds";
import { ApiError } from "../../api/client";
import { RatingButtons } from "./RatingButtons";

vi.mock("../../api/builds");
// O voto atual é lido ao abrir a build (#50); nestes testes ninguém votou ainda.
beforeEach(() => {
  vi.mocked(getMyRating).mockResolvedValue(null);
});


describe("RatingButtons", () => {
  it("clicar 👍 envia UP e marca o botão como selecionado", async () => {
    vi.mocked(rateBuild).mockResolvedValue(undefined);
    render(<RatingButtons buildId="X" />);

    await userEvent.click(screen.getByRole("button", { name: /Ajudou/ }));

    expect(rateBuild).toHaveBeenCalledWith("X", "UP");
    expect(screen.getByRole("button", { name: /^👍/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /Não ajudou/ })).toHaveAttribute("aria-pressed", "false");
    expect(await screen.findByRole("status")).toHaveTextContent("Obrigado pela avaliação");
  });

  it("trocar para 👎 envia DOWN", async () => {
    vi.mocked(rateBuild).mockResolvedValue(undefined);
    render(<RatingButtons buildId="X" />);

    await userEvent.click(screen.getByRole("button", { name: /^👍/ }));
    await userEvent.click(screen.getByRole("button", { name: /Não ajudou/ }));

    expect(rateBuild).toHaveBeenLastCalledWith("X", "DOWN");
    expect(screen.getByRole("button", { name: /Não ajudou/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("falha no envio mostra aviso e desfaz a seleção", async () => {
    vi.mocked(rateBuild).mockRejectedValue(new ApiError(0, "Não foi possível falar com o servidor."));
    render(<RatingButtons buildId="X" />);

    await userEvent.click(screen.getByRole("button", { name: /^👍/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível registrar sua avaliação");
    expect(screen.getByRole("button", { name: /^👍/ })).toHaveAttribute("aria-pressed", "false");
  });
});
