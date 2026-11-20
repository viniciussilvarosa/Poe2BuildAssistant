import { ApiError, apiGet, apiPost } from "./client";

/** Ataques ao cliente HTTP (card #2): respostas hostis viram sempre ApiError. */
describe("api/client — respostas hostis", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("F8: 200 com HTML (portal cativo, proxy) vira ApiError, não SyntaxError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("<html>Faça login no Wi-Fi</html>", { status: 200, headers: { "Content-Type": "text/html" } })),
    );

    const error = await apiGet("/api/x").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).detail).toMatch(/Tente novamente/);
  });

  it("F8: 200 com JSON truncado no POST vira ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response('{"id": "abc', { status: 200, headers: { "Content-Type": "application/json" } })),
    );

    await expect(apiPost("/api/x", {})).rejects.toBeInstanceOf(ApiError);
  });
});
