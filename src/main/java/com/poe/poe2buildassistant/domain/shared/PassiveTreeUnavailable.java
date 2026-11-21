package com.poe.poe2buildassistant.domain.shared;

/** Falha ao importar o export oficial da árvore de passivas (card #6). Nada é gravado. */
public final class PassiveTreeUnavailable extends DomainException {

    public PassiveTreeUnavailable(String detail) {
        super("Não foi possível baixar o export da árvore de passivas: " + detail);
    }
}
