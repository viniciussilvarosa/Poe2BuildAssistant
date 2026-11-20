package com.poe.poe2buildassistant.domain.catalog;

/** Classe base do personagem (ex. */
public record CharacterClass(String name) {

    public CharacterClass {
        name = CatalogNames.requireName(name, " Ranger).:classe");
    }
}
