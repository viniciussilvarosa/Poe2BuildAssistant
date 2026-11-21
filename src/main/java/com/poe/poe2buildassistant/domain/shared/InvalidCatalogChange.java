package com.poe.poe2buildassistant.domain.shared;

/** Mudança no rascunho que deixaria o catálogo incoerente (ex.: ascendência sem classe). */
public final class InvalidCatalogChange extends DomainException {

    public InvalidCatalogChange(String detail) {
        super("Mudança recusada: " + detail + ".");
    }
}
