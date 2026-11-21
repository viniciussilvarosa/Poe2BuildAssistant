package com.poe.poe2buildassistant.domain.shared;

public final class DraftAlreadyExists extends DomainException {

    public DraftAlreadyExists(String patch) {
        super("Já existe um rascunho do catálogo (versão " + patch + "). Publique ou continue editando esse rascunho.");
    }
}
