package com.poe.poe2buildassistant.domain.shared;

/** Falha ao importar o export oficial da árvore de passivas (card #6). Nada é gravado. */
public final class PassiveTreeFormatChanged extends DomainException {

    public PassiveTreeFormatChanged(String detail) {
        super("O export da árvore de passivas mudou de formato e não pôde ser importado: " + detail);
    }
}
