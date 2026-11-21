import { apiGet } from "./client";

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
