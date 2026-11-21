import { ApiError, apiGet, apiPost } from "./client";

function respondWith(status: number, body: unknown, contentType = "application/problem+json") {
  return vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": contentType } }),
  );
}

describe("api/client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("envia X-XSRF-TOKEN a partir do cookie em requisições POST", async () => {
    document.cookie = "XSRF-TOKEN=abc";
    const fetchMock = respondWith(200, { ok: true }, "application/json");
    vi.stubGlobal("fetch", fetchMock);

    await apiPost("/api/x", {});

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/x",
      expect.objectContaining({ headers: expect.objectContaining({ "X-XSRF-TOKEN": "abc" }) }),
    );
  });

  it("converte resposta ProblemDetail em ApiError com a mensagem pt-BR", async () => {
    vi.stubGlobal(
      "fetch",
      respondWith(429, { title: "Limite diário atingido", detail: "Limite diário atingido", retryAt: "2026-11-22T00:00:00Z" }),
    );

    await expect(apiPost("/api/x", {})).rejects.toMatchObject({
      status: 429,
      detail: "Limite diário atingido",
      retryAt: "2026-11-22T00:00:00Z",
    });
  });

  it("erro sem corpo JSON vira ApiError com mensagem genérica em pt-BR", async () => {
    vi.stubGlobal("fetch", respondWith(502, undefined, "text/html"));

    const error = await apiGet("/api/x").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 502 });
    expect((error as ApiError).detail).toMatch(/Tente novamente/);
  });

  it("falha de rede vira ApiError status 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(apiGet("/api/x")).rejects.toMatchObject({ status: 0 });
  });

  it("GET não envia CSRF e usa a sessão (same-origin)", async () => {
    const fetchMock = respondWith(200, { ok: true }, "application/json");
    vi.stubGlobal("fetch", fetchMock);

    await apiGet("/api/x");

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.credentials).toBe("same-origin");
    expect(init.headers).not.toHaveProperty("X-XSRF-TOKEN");
  });
});
