import { apiGet, apiSend } from "./client";

export interface Me {
  name: string;
  role: "USER" | "ADMIN";
}

/** Quem está logado, ou null para visitante (401) e para qualquer falha: o login é opcional. */
export async function getMe(): Promise<Me | null> {
  try {
    return await apiGet<Me>("/api/me");
  } catch {
    return null;
  }
}

/** Item de GET /api/me/builds (card #14), do mais recente para o mais antigo. */
export interface MyBuild {
  id: string;
  createdAt: string;
  concept: string;
  characterClass: string;
  ascendancy: string;
  mainSkill: string;
}

/** @throws ApiError 401 para visitante */
export const getMyBuilds = () => apiGet<MyBuild[]>("/api/me/builds");

/** Encerra a sessão no servidor (POST com CSRF; 204). */
export const logout = () => apiSend<void>("POST", "/logout");
