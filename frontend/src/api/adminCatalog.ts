import { ApiError, apiGet, apiSend } from "./client";

/** Contratos de /api/admin/catalog/drafts (card #17). Só funcionam para ADMIN. */
export interface CatalogVersionInfo {
  id: number;
  patch: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
}

export interface DraftCatalog {
  version: CatalogVersionInfo;
  classes: { name: string; ascendancies: string[] }[];
  weaponTypes: string[];
  skillGems: { name: string; weaponTypes: string[] }[];
  supportGems: string[];
  playstyles: string[];
  notables: number;
}

export type ItemType = "classes" | "ascendancies" | "weapon-types" | "skill-gems" | "support-gems" | "playstyles";

export interface ItemBody {
  name: string;
  characterClass?: string;
  weaponTypes?: string[];
}

const BASE = "/api/admin/catalog/drafts";

export async function getCurrentDraft(): Promise<CatalogVersionInfo | null> {
  try {
    return await apiGet<CatalogVersionInfo>(`${BASE}/current`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export const createDraft = (patch: string) => apiSend<CatalogVersionInfo>("POST", BASE, { patch });
export const getDraft = (id: number) => apiGet<DraftCatalog>(`${BASE}/${id}`);
export const saveItem = (id: number, type: ItemType, body: ItemBody) => apiSend<void>("PUT", `${BASE}/${id}/${type}`, body);
export const removeItem = (id: number, type: ItemType, name: string) =>
  apiSend<void>("DELETE", `${BASE}/${id}/${type}/${encodeURIComponent(name)}`);
export const publishDraft = (id: number) => apiSend<CatalogVersionInfo>("POST", `${BASE}/${id}/publish`);
export const importPassiveTree = (id: number) =>
  apiSend<{ notables: number }>("POST", `${BASE}/${id}/passive-tree/import`);
