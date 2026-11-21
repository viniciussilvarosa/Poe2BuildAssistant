import { ApiError } from "./client";
import { generateBuild } from "./builds";

/** Ataques ao leitor de SSE (card #12, PR #32). */

function byteStream(parts: Uint8Array[]) {
  return new Response(
    new ReadableStream<Uint8Array>({
      start(c) {
        parts.forEach((p) => c.enqueue(p));
        c.close();
      },
    }),
    { status: 200, headers: { "Content-Type": "text/event-stream" } },
  );
}

const text = (s: string) => new TextEncoder().encode(s);

afterEach(() => vi.unstubAllGlobals());

describe("H5 — bytes e formato hostis", () => {
  it("caractere UTF-8 partido entre pedaços chega inteiro", async () => {
    const all = text('event:done\ndata:{"id":"x","nome":"À distância"}\n\n');
    const cut = all.indexOf(0xc3) + 1; // no meio do "À"
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(byteStream([all.slice(0, cut), all.slice(cut)])));

    await expect(generateBuild({ concept: "x" }, () => {})).resolves.toMatchObject({ nome: "À distância" });
  });

  it("\\r e \\n do mesmo fim de linha em pedaços diferentes não partem o evento", async () => {
    const progress = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        byteStream([text('event:progress\r'), text('\ndata:{"step":"GENERATING","percent":40}\r\n\r\nevent:done\r\ndata:{"id":"y"}\r\n\r\n')]),
      ),
    );

    await expect(generateBuild({ concept: "x" }, progress)).resolves.toMatchObject({ id: "y" });
    expect(progress).toHaveBeenCalledWith({ step: "GENERATING", percent: 40 });
  });

  it("comentários de keep-alive (':') e eventos desconhecidos são ignorados", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(byteStream([text(':ping\n\nevent:novidade\ndata:{}\n\nevent:done\ndata:{"id":"z"}\n\n')])),
    );

    await expect(generateBuild({ concept: "x" }, () => {})).resolves.toMatchObject({ id: "z" });
  });

  it("JSON cortado no data vira ApiError, não SyntaxError", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(byteStream([text('event:done\ndata:{"id":\n\n')])));

    const e = await generateBuild({ concept: "x" }, () => {}).catch((err: unknown) => err);

    expect(e).toBeInstanceOf(ApiError);
  });

  it("200 com HTML de portal cativo em vez de stream vira ApiError", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("<html>Faça login no Wi-Fi</html>", { status: 200, headers: { "Content-Type": "text/html" } })),
    );

    const e = await generateBuild({ concept: "x" }, () => {}).catch((err: unknown) => err);

    expect(e).toBeInstanceOf(ApiError);
  });
});
