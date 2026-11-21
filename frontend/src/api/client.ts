/**
 * Único ponto do app que conhece HTTP (ADR-002 § 7). Mesma origem do backend: a sessão vai no
 * cookie e o CSRF é lido do cookie XSRF-TOKEN e devolvido no header, como o Spring espera.
 */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
    readonly retryAt?: string,
  ) {
    super(detail);
    this.name = "ApiError";
  }
}

export const GENERIC_ERROR = "Não foi possível falar com o servidor. Tente novamente em instantes.";

export function csrfToken(): string | undefined {
  const cookie = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)) : undefined;
}

export async function toApiError(response: Response): Promise<ApiError> {
  try {
    const problem = (await response.json()) as { detail?: string; retryAt?: string };
    return new ApiError(response.status, problem.detail ?? GENERIC_ERROR, problem.retryAt);
  } catch {
    return new ApiError(response.status, GENERIC_ERROR);
  }
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { credentials: "same-origin", ...init });
  } catch {
    throw new ApiError(0, GENERIC_ERROR);
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  try {
    return (await response.json()) as T;
  } catch {
    // 200 com HTML (portal cativo, proxy) ou JSON cortado: as telas só tratam ApiError (chaos Y3).
    throw new ApiError(response.status, GENERIC_ERROR);
  }
}

export function apiGet<T>(path: string): Promise<T> {
  return request<T>(path, { method: "GET", headers: { Accept: "application/json" } });
}

export function apiPost<T>(path: string, body: unknown): Promise<T> {
  return apiSend<T>("POST", path, body);
}

/** POST, PUT ou DELETE: leva o CSRF, como o Spring exige em toda requisição que altera estado. */
export function apiSend<T>(method: "POST" | "PUT" | "DELETE", path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const token = csrfToken();
  if (token) {
    headers["X-XSRF-TOKEN"] = token;
  }
  return request<T>(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
}
