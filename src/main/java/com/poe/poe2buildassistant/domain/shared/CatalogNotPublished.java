package com.poe.poe2buildassistant.domain.shared;

/** Não existe versão publicada do catálogo: sem ela, nenhuma build pode ser gerada ou validada. */
public final class CatalogNotPublished extends DomainException {

    public CatalogNotPublished() {
        super("O catálogo do jogo ainda não foi publicado. Tente novamente mais tarde.");
    }
}
