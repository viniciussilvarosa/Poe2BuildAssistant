import { ApiError } from "./client";
import { generateBuild, type ProgressEvent } from "./builds";

/** Resposta SSE entregue em pedaços arbitrários, como a rede faz. */
function sseResponse(chunks: string[], status = 200) {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
      controller.close();
    },
  });
  return new Response(body, { status, headers: { "Content-Type": "text/event-stream" } });
}

describe("api/builds generateBuild", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("faz POST com CSRF e entrega progress e done, mesmo com eventos partidos entre pedaços", async () => {
    document.cookie = "XSRF-TOKEN=tok";
    const fetchMock = vi.fn().mockResolvedValue(
      sseResponse([
        'event:progress\ndata:{"step":"VALIDATING_INPUT","per',
        'cent":10}\n\nevent:progress\r\ndata:{"step":"GENERATING","percent":40}\r\n\r\n',
        'event:done\ndata:{"id":"abc"}\n\n',
      ]),
    );
    vi.stubGlobal("fetch", fetchMock);
    const events: ProgressEvent[] = [];

    const build = await generateBuild({ concept: "x" }, (e) => events.push(e));

    expect(build).toMatchObject({ id: "abc" });
    expect(events).toEqual([
      { step: "VALIDATING_INPUT", percent: 10 },
      { step: "GENERATING", percent: 40 },
    ]);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/builds/generate",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ concept: "x" }),
        headers: expect.objectContaining({ "X-XSRF-TOKEN": "tok", Accept: "text/event-stream" }),
      }),
    );
  });

  it("evento error vira ApiError com status, mensagem e retryAt", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        sseResponse(['event:error\ndata:{"status":429,"detail":"Limite diário","retryAt":"2026-11-22T00:00:00Z"}\n\n']),
      ),
    );

    await expect(generateBuild({ concept: "x" }, () => {})).rejects.toMatchObject({
      status: 429,
      detail: "Limite diário",
      retryAt: "2026-11-22T00:00:00Z",
    });
  });

  it("400 antes do stream vira ApiError com o ProblemDetail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ detail: "O conceito é longo demais." }), {
          status: 400,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );

    await expect(generateBuild({ concept: "x" }, () => {})).rejects.toMatchObject({
      status: 400,
      detail: "O conceito é longo demais.",
    });
  });

  it("stream que termina sem done nem error vira ApiError genérico e é registrado", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sseResponse(['event:progress\ndata:{"step":"GENERATING","percent":40}\n\n'])));
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    const error = await generateBuild({ concept: "x" }, () => {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).detail).toMatch(/Tente novamente/);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it("falha de rede vira ApiError status 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(generateBuild({ concept: "x" }, () => {})).rejects.toMatchObject({ status: 0 });
  });
});
