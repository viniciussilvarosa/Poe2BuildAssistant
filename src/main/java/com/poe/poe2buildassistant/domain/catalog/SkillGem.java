package com.poe.poe2buildassistant.domain.catalog;

/** Gema de habilidade (ex. */
public record SkillGem(String name) {

    public SkillGem {
        name = CatalogNames.requireName(name, " Ice Shot).:gema de habilidade");
    }
}
