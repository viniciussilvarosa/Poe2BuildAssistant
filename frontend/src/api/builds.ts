/** Corpo do POST /api/builds/generate. Seletor ausente = "Deixar a IA escolher". */
export interface BuildRequest {
  concept: string;
  characterClass?: string;
  ascendancy?: string;
  weaponType?: string;
  mainSkill?: string;
  playstyle?: string;
}
