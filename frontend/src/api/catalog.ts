import { apiGet } from "./client";

/** Contrato de GET /api/catalog/options (card #7). Nomes de jogo em inglês, como no catálogo. */
export interface CatalogOptions {
  patch: string;
  classes: { name: string; ascendancies: { name: string }[] }[];
  weaponTypes: { name: string }[];
  /** weaponTypes vazio = a gema não exige arma. */
  skillGems: { name: string; weaponTypes: string[] }[];
  playstyles: { name: string }[];
}

export function getCatalogOptions(): Promise<CatalogOptions> {
  return apiGet<CatalogOptions>("/api/catalog/options");
}
