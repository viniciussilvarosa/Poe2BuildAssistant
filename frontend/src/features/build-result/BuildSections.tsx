import type { BuildContent } from "../../api/builds";
import styles from "./BuildResultPage.module.css";

/**
 * Rótulos pt-BR para os campos que a IA justifica. A chave vem do LLM em formas variadas
 * ("characterClass", "passiveTree.notables", "gear.weapon", "campaignTips.0"); nenhuma chave técnica
 * aparece na tela: o que não for reconhecido vira "Escolha da IA" (bug #49).
 */
const FIELD_LABELS: Record<string, string> = {
  characterclass: "Classe",
  class: "Classe",
  classe: "Classe",
  ascendancy: "Ascendência",
  ascendência: "Ascendência",
  weapontype: "Arma",
  weapon: "Arma",
  arma: "Arma",
  mainskill: "Habilidade principal",
  skill: "Habilidade principal",
  playstyle: "Estilo de jogo",
  supports: "Gemas de suporte",
  supportgems: "Gemas de suporte",
  suportes: "Gemas de suporte",
  notables: "Notables",
  passivetreenotables: "Notables",
  passivetree: "Árvore de passivas",
  passivetreedirection: "Árvore de passivas",
  gear: "Equipamento",
  gearweapon: "Equipamento",
  geararmour: "Equipamento",
  gearaccessories: "Equipamento",
  campaigntips: "Dicas de campanha",
};

/** O LLM preenche field/reason livremente e o validador não confere essa lista (chaos C1a). */
function fieldLabel(field: string | null | undefined) {
  if (!field?.trim()) return "Escolha da IA";
  // Sem espaço, _ e -, sem índices de lista ("campaignTips.0"); tenta o caminho inteiro e depois a raiz.
  const parts = field.trim().toLowerCase().replace(/[\s_-]/g, "").split(".").filter((p) => p && !/^\d+$/.test(p));
  return FIELD_LABELS[parts.join("")] ?? FIELD_LABELS[parts[0] ?? ""] ?? "Escolha da IA";
}

/* Todo texto vem do LLM: só como texto (React escapa), nunca como HTML. */

export function SummarySection({ build }: { build: BuildContent }) {
  // Justificativa sem texto não explica nada: some, em vez de virar rótulo solto (chaos C1b).
  const reasons = (build.choiceReasons ?? []).filter((c) => c?.reason?.trim());
  return (
    <section aria-labelledby="secao-resumo">
      <h2 id="secao-resumo">Resumo &amp; Sinergia</h2>
      <p>{build.summary}</p>
      {build.synergy && <p>{build.synergy}</p>}
      {reasons.length > 0 && (
        <dl className={styles.escolhas}>
          {reasons.map((c, i) => (
            <div key={i}>
              <dt>{fieldLabel(c.field)}</dt>
              <dd>{c.reason}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}

export function GemsSection({ build }: { build: BuildContent }) {
  return (
    <section aria-labelledby="secao-gemas">
      <h2 id="secao-gemas">Gemas</h2>
      <p className={styles.rotulo}>Habilidade principal</p>
      <p className={styles.gemaPrincipal}>{build.mainSkill}</p>
      <p className={styles.rotulo}>Suportes</p>
      <ul className={styles.lista}>
        {build.supports.map((s) => (
          <li key={s}>{s}</li>
        ))}
      </ul>
    </section>
  );
}

export function PassivesSection({ build }: { build: BuildContent }) {
  return (
    <section aria-labelledby="secao-passivas">
      <h2 id="secao-passivas">Árvore de Passivas</h2>
      {build.passiveTree.direction && <p>{build.passiveTree.direction}</p>}
      <p className={styles.rotulo}>Notables para buscar</p>
      <ul className={styles.lista}>
        {build.passiveTree.notables.map((n) => (
          <li key={n}>{n}</li>
        ))}
      </ul>
    </section>
  );
}

export function GearSection({ build }: { build: BuildContent }) {
  const slots: [string, string[]][] = [
    ["Arma", build.gear.weapon],
    ["Armadura", build.gear.armour],
    ["Acessórios", build.gear.accessories],
  ];
  return (
    <section aria-labelledby="secao-equipamento">
      <h2 id="secao-equipamento">Prioridades de Equipamento</h2>
      <dl className={styles.slots}>
        {slots
          .filter(([, values]) => values.length > 0)
          .map(([slot, values]) => (
            <div key={slot}>
              <dt>{slot}</dt>
              <dd>{values.join(", ")}</dd>
            </div>
          ))}
      </dl>
    </section>
  );
}

export function TipsSection({ build }: { build: BuildContent }) {
  return (
    <section aria-labelledby="secao-dicas">
      <h2 id="secao-dicas">Dicas de campanha</h2>
      <ul className={styles.dicas}>
        {build.campaignTips.map((tip, i) => (
          <li key={i}>{tip}</li>
        ))}
      </ul>
    </section>
  );
}
