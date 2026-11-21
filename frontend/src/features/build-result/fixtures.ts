import type { SavedBuild } from "../../api/builds";

/** Build sintética: Ranger/Deadeye/Bow/Ice Shot, como a fixture do backend. */
export function buildFixture(overrides: Partial<SavedBuild["build"]> = {}): SavedBuild {
  return {
    id: "00000000-0000-0000-0000-000000000042",
    createdAt: "2026-11-21T15:00:00Z",
    request: { concept: "arqueiro mágico", characterClass: null, ascendancy: null, weaponType: "Bow", mainSkill: null, playstyle: null },
    build: {
      summary: "Arqueiro de gelo com alta mobilidade.",
      synergy: "Ice Shot congela e Pierce atinge vários inimigos.",
      choiceReasons: [],
      characterClass: "Ranger",
      ascendancy: "Deadeye",
      weaponType: "Bow",
      mainSkill: "Ice Shot",
      supports: ["Pierce"],
      passiveTree: {
        direction: "Lado da Ranger, rumo a projéteis e frio.",
        notables: ["Swift Flight", "Cold Precision", "Frozen Edge", "Agile Feet", "Iron Skin"],
      },
      gear: { weapon: ["Dano de gelo"], armour: ["Vida máxima"], accessories: ["Resistências"] },
      campaignTips: ["Pegue a Ascendência no Ato 2."],
      ...overrides,
    },
  };
}
