package com.poe.poe2buildassistant.domain.catalog;

/** Estilo de jogo oferecido no formulário (ex. */
public record Playstyle(String name) {

    public Playstyle {
        name = CatalogNames.requireName(name, " Ranged).:estilo de jogo");
    }
}
