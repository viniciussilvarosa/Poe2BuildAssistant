package com.poe.poe2buildassistant.domain.catalog;

import java.util.Objects;

/** Versão do catálogo, uma por patch do jogo. Só uma fica publicada por vez. */
public record CatalogVersion(long id, String patch, Status status) {

    /** Rascunho (editável) → publicada (a vigente, imutável) → arquivada (nunca apagada: builds antigas apontam para ela). */
    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    /** Tamanho da coluna catalog_version.patch. */
    public static final int MAX_PATCH_LENGTH = 50;

    public CatalogVersion {
        patch = CatalogNames.requireName(patch, "versão do catálogo");
        Objects.requireNonNull(status, "versão do catálogo sem status");
    }
}
