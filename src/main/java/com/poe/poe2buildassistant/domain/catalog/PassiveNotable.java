package com.poe.poe2buildassistant.domain.catalog;

/** Notable da árvore de passivas, vindo do export oficial da GGG. */
public record PassiveNotable(String name, String description) {

    public PassiveNotable {
        name = CatalogNames.requireName(name, "notable");
        description = description == null ? "" : description.strip();
    }
}
