/**
 * Conteúdo do guia rápido para quem é novo no Path of Exile 2, mas já jogou outros RPGs: explica só
 * o que é próprio do jogo. Fica separado da página para poder ser reaproveitado (ex.: glossário no
 * resultado da build). Nomes de jogo em inglês, como aparecem no jogo e no catálogo (patch 0.5).
 */

import bloodmage from "../../assets/guide/bloodmage.jpg";
import chronomancer from "../../assets/guide/chronomancer.jpg";
import deadeye from "../../assets/guide/deadeye.jpg";
import defesa from "../../assets/guide/defesa.jpg";
import itens from "../../assets/guide/itens.jpg";
import mercenary from "../../assets/guide/mercenary.jpg";

/**
 * Arte oficial do Path of Exile 2 ao lado da seção. "recorte" é personagem sobre fundo preto (o preto
 * se funde com a página); "arte" é ilustração com cenário. Legenda só quando sabemos quem é.
 */
export type GuideImage = {
  src: string;
  kind: "recorte" | "arte";
  caption?: string;
  /** Recorte largo com o personagem no meio: aproxima e enquadra (ex.: "55%" desloca para a direita). */
  focus?: string;
};

export type GuideSection = {
  id: string;
  title: string;
  paragraphs: string[];
  /** Ideias de conceito que exploram o tema da seção; abrem o formulário já preenchido. */
  ideas: string[];
  image?: GuideImage;
};

export const GUIDE_SECTIONS: GuideSection[] = [
  {
    id: "gemas",
    title: "As habilidades vêm das gemas",
    paragraphs: [
      "No Path of Exile 2, a classe não define a sua lista de habilidades. Você ganha habilidades equipando gemas de habilidade (skill gems), e qualquer classe pode usar qualquer gema.",
      "As gemas de suporte (support gems) se ligam a uma habilidade e mudam como ela funciona: mais projéteis, área maior, dano extra de um elemento. Boa parte da força de uma build está na escolha dos suportes.",
    ],
    ideas: ["magia de fogo que explode em grandes áreas", "flechas que se dividem e acertam vários inimigos"],
    image: { src: chronomancer, kind: "arte", caption: "Chronomancer, uma das ascendências da Sorceress" },
  },
  {
    id: "armas",
    title: "A arma decide o que você pode usar",
    paragraphs: [
      "Muitas habilidades de ataque exigem um tipo de arma: Ice Shot e Lightning Arrow só funcionam com arco (Bow), as granadas pedem besta (Crossbow) e golpes como Ice Strike pedem bastão (Quarterstaff). Magias, em geral, funcionam com qualquer arma.",
      "Por isso o formulário pergunta a arma: escolher a arma primeiro já filtra as habilidades possíveis.",
    ],
    ideas: ["lanceiro que arremessa lanças de raio", "besteiro que bombardeia grupos com granadas"],
    image: { src: mercenary, kind: "recorte", caption: "Mercenary com besta (Crossbow)" },
  },
  {
    id: "classes",
    title: "Classe e ascendência",
    paragraphs: [
      "A classe define onde você começa na árvore de passivas e puxa para um estilo, mas não prende você a ele.",
      "Durante a campanha você libera a ascendência (ascendancy), uma especialização da classe com poderes fortes, como Deadeye ou Pathfinder para a Ranger. É a escolha que mais muda a cara da build.",
    ],
    ideas: ["guerreiro com martelo que faz o chão tremer", "druida que se transforma em fera"],
    image: { src: deadeye, kind: "recorte", caption: "Deadeye, uma das ascendências da Ranger", focus: "55%" },
  },
  {
    id: "passivas",
    title: "A árvore de passivas",
    paragraphs: [
      "Cada nível dá um ponto numa árvore enorme, a mesma para todas as classes. Os nós pequenos dão bônus pequenos; os notables (nós maiores, com nome próprio) são os que fazem diferença.",
      "Uma build forte segue numa direção da árvore em vez de espalhar pontos. O mentor indica a região e os notables que valem a pena.",
    ],
    ideas: [],
    image: { src: bloodmage, kind: "arte", caption: "Blood Mage, uma das ascendências da Witch" },
  },
  {
    id: "defesa",
    title: "Camadas de defesa",
    paragraphs: [
      "Armour reduz o dano físico dos golpes. Evasion dá chance de evitar ataques. Energy Shield é uma camada extra acima da vida, que se recarrega quando você passa um tempo sem levar dano.",
      "Elas vêm principalmente dos equipamentos, e é possível misturar duas. Em geral, personagens corpo a corpo puxam armour, arqueiros puxam evasion e conjuradores puxam energy shield.",
    ],
    ideas: ["conjuradora protegida por energy shield que congela inimigos", "guerreiro com escudo que aguenta qualquer chefe"],
    image: { src: defesa, kind: "recorte" },
  },
  {
    id: "itens",
    title: "Nos itens, procure modificadores",
    paragraphs: [
      "Itens raros têm modificadores aleatórios. Em vez de caçar um item específico, você procura modificadores: vida máxima, resistências elementais, dano do seu elemento, velocidade de movimento nas botas.",
      "Resistências contam muito: o jogo aplica penalidades nelas conforme você avança, e o máximo normal é 75%. Manter fogo, gelo e raio altos evita mortes de surpresa nos mapas.",
    ],
    ideas: [],
    image: { src: itens, kind: "recorte", focus: "47%" },
  },
];

/** Classes do patch 0.5, com o estilo a que cada uma costuma puxar. */
export const GUIDE_CLASSES: { name: string; style: string }[] = [
  { name: "Warrior", style: "força, maças e golpes pesados" },
  { name: "Ranger", style: "arcos e projéteis" },
  { name: "Huntress", style: "lança e escudo" },
  { name: "Mercenary", style: "bestas e granadas" },
  { name: "Monk", style: "bastão, velocidade, raio e gelo" },
  { name: "Sorceress", style: "magias elementais" },
  { name: "Witch", style: "lacaios, caos e fogo" },
  { name: "Druid", style: "transformações e talismã" },
];

/** O que deixa um conceito mais claro para o mentor. */
export const CONCEPT_TIPS: { label: string; examples: string }[] = [
  { label: "Elemento ou tipo de dano", examples: "fogo, gelo, raio, caos, físico" },
  { label: "Distância", examples: "de longe, corpo a corpo, lacaios lutando por você" },
  { label: "Ritmo", examples: "rápido e móvel, lento e resistente" },
  { label: "Defesa (opcional)", examples: "armour, evasion, energy shield" },
];

export const CONCEPT_BEFORE = "mago";
export const CONCEPT_AFTER = "maga de gelo que congela grupos de longe e se protege com energy shield";

export function createLink(concept: string) {
  return `/criar?${new URLSearchParams({ conceito: concept })}`;
}
