package com.poe.poe2buildassistant.domain.shared;

/** A IA respondeu, mas não foi possível obter uma build utilizável (resposta malformada ou inválida). */
public final class GenerationFailed extends DomainException {

    public GenerationFailed() {
        super("Não foi possível gerar uma build válida agora. Tente novamente.");
    }
}
