package com.poe.poe2buildassistant.domain.shared;

public final class CatalogVersionNotFound extends DomainException {

    public CatalogVersionNotFound(long versionId) {
        super("A versão " + versionId + " do catálogo não existe.");
    }
}
