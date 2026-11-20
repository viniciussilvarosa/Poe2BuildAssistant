import styles from "./PassiveConstellation.module.css";

type Region = "forca" | "destreza" | "inteligencia";

/**
 * As três regiões da árvore de passivas, como no jogo: Força (embaixo, à esquerda), Destreza
 * (embaixo, à direita) e Inteligência (em cima), saindo do centro onde toda build começa.
 * Coordenadas num viewBox 400×400. As linhas são desenhadas uma única vez ao carregar.
 */
const CENTER = { x: 200, y: 205 };

const REGIONS: { region: Region; label: string; hub: [number, number]; nodes: [number, number][] }[] = [
  { region: "inteligencia", label: "Inteligência", hub: [200, 95], nodes: [[150, 50], [200, 28], [250, 50], [135, 105], [265, 105]] },
  { region: "forca", label: "Força", hub: [105, 270], nodes: [[48, 248], [40, 310], [88, 350], [150, 335], [70, 200]] },
  { region: "destreza", label: "Destreza", hub: [295, 270], nodes: [[352, 248], [360, 310], [312, 350], [250, 335], [330, 200]] },
];

export function PassiveConstellation() {
  return (
    <figure className={styles.figura}>
      <svg viewBox="0 0 400 400" className={styles.ceu} aria-hidden="true">
        {REGIONS.map(({ region, hub, nodes }, i) => (
          <g key={region} className={styles[region]} style={{ ["--ordem" as string]: i }}>
            <line className={styles.linha} pathLength={1} x1={CENTER.x} y1={CENTER.y} x2={hub[0]} y2={hub[1]} />
            {nodes.map(([x, y]) => (
              <line key={`l${x}-${y}`} className={styles.linha} pathLength={1} x1={hub[0]} y1={hub[1]} x2={x} y2={y} />
            ))}
            {nodes.map(([x, y]) => (
              <circle key={`n${x}-${y}`} className={styles.no} cx={x} cy={y} r={6} />
            ))}
            <circle className={styles.notable} cx={hub[0]} cy={hub[1]} r={11} />
          </g>
        ))}
        <circle className={styles.inicio} cx={CENTER.x} cy={CENTER.y} r={16} />
      </svg>
      <figcaption className={styles.legenda}>
        <span>Regiões da árvore de passivas</span>
        <ul>
          {REGIONS.map(({ region, label }) => (
            <li key={region} className={styles[region]}>
              {label}
            </li>
          ))}
        </ul>
      </figcaption>
    </figure>
  );
}
