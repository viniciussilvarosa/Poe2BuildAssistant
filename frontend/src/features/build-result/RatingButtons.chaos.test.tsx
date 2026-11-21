import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { getMyRating, rateBuild } from "../../api/builds";
import { ApiError } from "../../api/client";
import { RatingButtons } from "./RatingButtons";

vi.mock("../../api/builds");
// O voto atual é lido ao abrir a build (#50); nestes testes ninguém votou ainda.
beforeEach(() => {
  vi.mocked(getMyRating).mockResolvedValue(null);
});


/** Ataques aos botões 👍/👎 (card #16, PR #42). */

describe("H1 — respostas fora de ordem", () => {
  it("falha atrasada do 👍 não apaga o 👎 que já foi registrado", async () => {
    let falharUp: (e: unknown) => void = () => {};
    vi.mocked(rateBuild).mockImplementation((_id, value) =>
      value === "UP" ? new Promise((_, reject) => (falharUp = reject)) : Promise.resolve(),
    );
    render(<RatingButtons buildId="X" />);

    await userEvent.click(screen.getByRole("button", { name: /^👍/ }));
    await userEvent.click(screen.getByRole("button", { name: /Não ajudou/ }));
    await act(async () => falharUp(new ApiError(0, "rede")));

    expect(screen.getByRole("button", { name: /Não ajudou/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("sucesso atrasado do 👍 não sobrescreve a seleção do 👎 feita depois", async () => {
    let concluirUp: () => void = () => {};
    vi.mocked(rateBuild).mockImplementation((_id, value) =>
      value === "UP" ? new Promise<void>((resolve) => (concluirUp = resolve)) : Promise.resolve(),
    );
    render(<RatingButtons buildId="X" />);

    await userEvent.click(screen.getByRole("button", { name: /^👍/ }));
    await userEvent.click(screen.getByRole("button", { name: /Não ajudou/ }));
    await act(async () => concluirUp());

    expect(screen.getByRole("button", { name: /Não ajudou/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /^👍/ })).toHaveAttribute("aria-pressed", "false");
  });
});
