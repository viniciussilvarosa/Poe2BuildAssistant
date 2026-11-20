package com.poe.poe2buildassistant.domain.shared;

/** Base das exceções de regra de negócio. Mensagens em pt-BR, prontas para o usuário. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
