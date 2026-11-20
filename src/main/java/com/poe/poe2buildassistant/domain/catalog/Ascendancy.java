package com.poe.poe2buildassistant.domain.catalog;

import java.util.Objects;

/** Ascendência (especialização) de uma classe base (ex.: Deadeye da Ranger). */
public record Ascendancy(String name, CharacterClass characterClass) {

    public Ascendancy {
        name = CatalogNames.requireName(name, "ascendência");
        Objects.requireNonNull(characterClass, "ascendência sem classe");
    }

    public boolean belongsTo(CharacterClass other) {
        return CatalogNames.normalize(characterClass.name()).equals(CatalogNames.normalize(other.name()));
    }
}
