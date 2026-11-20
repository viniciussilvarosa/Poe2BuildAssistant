package com.poe.poe2buildassistant.domain.catalog;

import java.util.Objects;

/** Versão do catálogo, uma por patch do jogo. Só uma fica publicada por vez. */
public record CatalogVersion(long id, String patch, Status status) {

    public enum Status {
        DRAFT,
        PUBLISHED
    }

    public CatalogVersion {
        patch = CatalogNames.requireName(patch, "versão do catálogo");
        Objects.requireNonNull(status, "versão do catálogo sem status");
    }
}
