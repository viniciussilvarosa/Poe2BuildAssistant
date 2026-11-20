package com.poe.poe2buildassistant.domain;

import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft.GearPriorities;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft.PassiveDirection;
import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.catalog.Playstyle;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.SupportGem;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import java.util.List;

/** Catálogo e drafts sintéticos para testes de domínio. */
public final class CatalogFixtures {

    public static final CharacterClass RANGER = new CharacterClass("Ranger");
    public static final CharacterClass WITCH = new CharacterClass("Witch");

    public static final List<String> FIVE_NOTABLES =
            List.of("Swift Flight", "Cold Precision", "Frozen Edge", "Agile Feet", "Iron Skin");

    private CatalogFixtures() {}

    public static Catalog catalog() {
        return Catalog.builder(new CatalogVersion(1, "0.4", CatalogVersion.Status.PUBLISHED))
                .characterClass(RANGER)
                .characterClass(WITCH)
                .ascendancy(new Ascendancy("Deadeye", RANGER))
                .ascendancy(new Ascendancy("Pathfinder", RANGER))
                .ascendancy(new Ascendancy("Infernalist", WITCH))
                .weaponType(new WeaponType("Bow"))
                .weaponType(new WeaponType("Staff"))
                .skillGem(new SkillGem("Ice Shot"))
                .skillGem(new SkillGem("Fireball"))
                .supportGem(new SupportGem("Pierce"))
                .supportGem(new SupportGem("Cold Infusion"))
                .supportGem(new SupportGem("Elemental Focus"))
                .notables(FIVE_NOTABLES.stream().map(n -> new PassiveNotable(n, "")).toList())
                .notable(new PassiveNotable("Spare Notable", ""))
                .playstyle(new Playstyle("Ranged"))
                .build();
    }

    /** Draft coerente com o catálogo: Ranger/Deadeye/Bow/Ice Shot. */
    public static GeneratedBuildDraft validDraft() {
        return draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce", "Cold Infusion"), FIVE_NOTABLES);
    }

    public static GeneratedBuildDraft draft(
            String characterClass,
            String ascendancy,
            String weaponType,
            String mainSkill,
            List<String> supports,
            List<String> notables) {
        return new GeneratedBuildDraft(
                "Arqueiro de gelo com alta mobilidade.",
                "Ice Shot congela e Pierce atinge vários inimigos.",
                List.of(),
                characterClass,
                ascendancy,
                weaponType,
                mainSkill,
                supports,
                new PassiveDirection("Lado da Ranger, rumo a projéteis e frio.", notables),
                new GearPriorities(List.of("Dano de gelo"), List.of("Vida máxima"), List.of("Resistências")),
                List.of("Pegue a Ascendência no Ato 2."));
    }
}
