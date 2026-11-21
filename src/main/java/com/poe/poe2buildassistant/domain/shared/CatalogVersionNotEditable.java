package com.poe.poe2buildassistant.domain.shared;

/** Versão publicada é só leitura: mudanças entram num rascunho, que depois é publicado. */
public final class CatalogVersionNotEditable extends DomainException {

    public CatalogVersionNotEditable(String patch) {
        super("A versão " + patch + " do catálogo já está publicada e não pode ser alterada. Edite um rascunho.");
    }
}
