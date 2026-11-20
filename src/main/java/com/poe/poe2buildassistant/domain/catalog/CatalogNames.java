package com.poe.poe2buildassistant.domain.catalog;

import java.util.Locale;

/** Regras de nomes do catálogo: comparação ignora caixa e espaços nas pontas. */
public final class CatalogNames {

    private CatalogNames() {}

    /** Chave de comparação de um nome do jogo. {@code null} vira string vazia. */
    public static String normalize(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    static String requireName(String name, String kind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(kind + " sem nome");
        }
        return name.strip();
    }
}
