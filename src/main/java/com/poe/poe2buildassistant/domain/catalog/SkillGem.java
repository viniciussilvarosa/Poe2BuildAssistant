package com.poe.poe2buildassistant.domain.catalog;

import java.util.List;

/**
 * Gema de habilidade (ex.: Ice Shot) e os tipos de arma com que ela pode ser usada. Lista vazia =
 * sem exigência de arma (ex.: a maioria dos feitiços).
 */
public record SkillGem(String name, List<String> weaponTypes) {

    public SkillGem {
        name = CatalogNames.requireName(name, "gema de habilidade");
        weaponTypes =
                weaponTypes == null
                        ? List.of()
                        : weaponTypes.stream().map(w -> CatalogNames.requireName(w, "tipo de arma")).distinct().toList();
    }

    /** Gema sem exigência de arma. */
    public SkillGem(String name) {
        this(name, List.of());
    }

    public boolean usableWith(WeaponType weaponType) {
        return weaponTypes.isEmpty()
                || weaponTypes.stream()
                        .anyMatch(w -> CatalogNames.normalize(w).equals(CatalogNames.normalize(weaponType.name())));
    }
}
