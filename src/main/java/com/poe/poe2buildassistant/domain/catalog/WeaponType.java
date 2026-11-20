package com.poe.poe2buildassistant.domain.catalog;

/** Tipo de arma (ex. */
public record WeaponType(String name) {

    public WeaponType {
        name = CatalogNames.requireName(name, " Bow).:tipo de arma");
    }
}
