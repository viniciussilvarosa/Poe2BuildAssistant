package com.poe.poe2buildassistant.domain.catalog;

/** Gema de suporte (ex. */
public record SupportGem(String name) {

    public SupportGem {
        name = CatalogNames.requireName(name, " Pierce).:gema de suporte");
    }
}
