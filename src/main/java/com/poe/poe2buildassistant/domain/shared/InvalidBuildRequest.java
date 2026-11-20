package com.poe.poe2buildassistant.domain.shared;

/** O pedido de build do usuário é inválido (conceito vazio, restrição inexistente ou incoerente). */
public final class InvalidBuildRequest extends DomainException {

    public InvalidBuildRequest(String message) {
        super(message);
    }
}
