import { ApiError, apiGet, apiSend, csrfToken, GENERIC_ERROR, toApiError } from "./client";

/** Corpo do POST /api/builds/generate. Seletor ausente = "Deixar a IA escolher". */
export interface BuildRequest {
  concept: string;
  characterClass?: string;
  ascendancy?: string;
  weaponType?: string;
  mainSkill?: string;
  playstyle?: string;
}

export type GenerationStep = "VALIDATING_INPUT" | "GENERATING" | "VALIDATING_OUTPUT" | "SAVING" | "DONE";

export interface ProgressEvent {
  step: GenerationStep;
  percent: number;
}

/** Conteúdo validado da build (GeneratedBuildDraft). Textos em pt-BR, nomes de jogo em inglês. */
export interface BuildContent {
  summary: string;
  synergy: string;
  /** Por que a IA escolheu cada campo deixado em aberto; field é livre (ex.: "characterClass"). */
  choiceReasons: { field: string; reason: string }[];
  characterClass: string;
  ascendancy: string;
  weaponType: string;
  mainSkill: string;
  supports: string[];
  passiveTree: { direction: string; notables: string[] };
  gear: { weapon: string[]; armour: string[]; accessories: string[] };
  campaignTips: string[];
}

/** Build salva, como o evento done e GET /api/builds/{id} devolvem (BuildResponse). */
export interface SavedBuild {
  id: string;
  createdAt: string;
  request: {
    concept: string;
    characterClass: string | null;
    ascendancy: string | null;
    weaponType: string | null;
    mainSkill: string | null;
    playstyle: string | null;
  };
  build: BuildContent;
}

/** Reabre uma build salva (link direto, "Minhas builds"). */
export function getBuild(id: string): Promise<SavedBuild> {
  return apiGet<SavedBuild>(`/api/builds/${encodeURIComponent(id)}`);
}

interface SseMessage {
  event: string;
  data: string;
}

/**
 * Gera a build lendo o SSE do POST. O EventSource nativo só faz GET, por isso fetch + stream.
 * Resolve no evento done; rejeita com ApiError no evento error ou em qualquer falha.
 */
export async function generateBuild(request: BuildRequest, onProgress: (event: ProgressEvent) => void): Promise<SavedBuild> {
  const headers: Record<string, string> = { "Content-Type": "application/json", Accept: "text/event-stream" };
  const token = csrfToken();
  if (token) {
    headers["X-XSRF-TOKEN"] = token;
  }
  let response: Response;
  try {
    response = await fetch("/api/builds/generate", {
      method: "POST",
      credentials: "same-origin",
      headers,
      body: JSON.stringify(request),
    });
  } catch {
    throw new ApiError(0, GENERIC_ERROR);
  }
  if (!response.ok || !response.body) {
    throw await toApiError(response);
  }

  try {
    for await (const message of readSse(response.body)) {
      const data = JSON.parse(message.data) as Record<string, unknown>;
      if (message.event === "progress") {
        onProgress(data as unknown as ProgressEvent);
      } else if (message.event === "done") {
        return data as unknown as SavedBuild;
      } else if (message.event === "error") {
        const status = typeof data.status === "number" ? data.status : 500;
        throw new ApiError(status, String(data.detail ?? GENERIC_ERROR), data.retryAt as string | undefined);
      }
    }
  } catch (e) {
    if (e instanceof ApiError) {
      throw e;
    }
    console.error("build.stream.failed", e);
    throw new ApiError(0, GENERIC_ERROR);
  }
  console.error("build.stream.ended_without_result");
  throw new ApiError(0, GENERIC_ERROR);
}

/** Separa o stream em mensagens SSE, mesmo quando uma mensagem chega partida entre pedaços. */
async function* readSse(body: ReadableStream<Uint8Array>): AsyncGenerator<SseMessage> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  for (;;) {
    const { value, done } = await reader.read();
    if (done) {
      return;
    }
    // stream: true segura bytes de um caractere UTF-8 partido entre pedaços ("À", "ç").
    // Um "\r" no fim do pedaço espera o próximo: pode ser a metade de um "\r\n".
    buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n|\r(?!$)/g, "\n");
    let end: number;
    while ((end = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);
      const message = parseBlock(block);
      if (message) {
        yield message;
      }
    }
  }
}

function parseBlock(block: string): SseMessage | undefined {
  let event = "message";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    const colon = line.indexOf(":");
    const field = colon < 0 ? line : line.slice(0, colon);
    const value = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
    if (field === "event") {
      event = value;
    } else if (field === "data") {
      data.push(value);
    }
  }
  return data.length ? { event, data: data.join("\n") } : undefined;
}

export type Rating = "UP" | "DOWN";

/** 👍/👎 da build (card #16). Votar de novo substitui o voto anterior. */
export function rateBuild(id: string, value: Rating): Promise<void> {
  return apiSend<void>("POST", `/api/builds/${encodeURIComponent(id)}/rating`, { value });
}

/** O voto que este avaliador já deu nesta build, ou null (204: ainda não votou). */
export async function getMyRating(id: string): Promise<Rating | null> {
  const body = await apiGet<{ value: Rating } | undefined>(`/api/builds/${encodeURIComponent(id)}/rating`);
  return body?.value ?? null;
}
