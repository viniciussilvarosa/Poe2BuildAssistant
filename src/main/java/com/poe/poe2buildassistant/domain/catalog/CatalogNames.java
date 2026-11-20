package com.poe.poe2buildassistant.domain.catalog;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras de nomes do catálogo: comparação ignora caixa, espaços nas pontas e diferenças de espaço
 * interno (espaço duplo, tab, espaço não separável), que o LLM e o curador podem introduzir.
 */
public final class CatalogNames {

    /** Qualquer sequência de espaço Unicode, inclusive U+00A0 (fora de Character.isWhitespace). */
    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    private CatalogNames() {}

    /** Chave de comparação de um nome do jogo. {@code null} vira string vazia. */
    public static String normalize(String name) {
        return name == null ? "" : collapse(name).toLowerCase(Locale.ROOT);
    }

    static String requireName(String name, String kind) {
        String collapsed = name == null ? "" : collapse(name);
        if (collapsed.isEmpty()) {
            throw new IllegalArgumentException(kind + " sem nome");
        }
        return collapsed;
    }

    private static String collapse(String name) {
        return WHITESPACE.matcher(name).replaceAll(" ").strip();
    }
}
