import type { SavedBuild } from "../../api/builds";

/**
 * Resumo em texto simples para colar no Discord, num bloco de notas ou no chat do jogo.
 * Rótulos em pt-BR; nomes de jogo em inglês, exatamente como no catálogo.
 */
export function formatBuildSummary({ request, build }: SavedBuild): string {
  const lines: string[] = [];
  const add = (label: string, values: string[]) => {
    if (values.length) lines.push(`${label}: ${values.join(", ")}`);
  };

  lines.push(`${build.mainSkill}: ${build.characterClass} (${build.ascendancy}), ${build.weaponType}`);
  lines.push(`Conceito: ${request.concept}`);
  lines.push("");
  lines.push(build.summary);
  if (build.synergy) lines.push(build.synergy);
  lines.push("");
  lines.push(`Habilidade principal: ${build.mainSkill}`);
  add("Suportes", build.supports);
  lines.push("");
  if (build.passiveTree.direction) lines.push(`Passivas: ${build.passiveTree.direction}`);
  add("Notables", build.passiveTree.notables);
  lines.push("");
  add("Arma", build.gear.weapon);
  add("Armadura", build.gear.armour);
  add("Acessórios", build.gear.accessories);
  if (build.campaignTips.length) {
    lines.push("");
    lines.push("Dicas de campanha:");
    build.campaignTips.forEach((tip) => lines.push(`- ${tip}`));
  }
  lines.push("");
  lines.push("Gerado pelo Mentor de Builds, projeto independente não afiliado à Grinding Gear Games.");

  // Junta seções vazias consecutivas numa só linha em branco.
  return lines.join("\n").replace(/\n{3,}/g, "\n\n").trim();
}
