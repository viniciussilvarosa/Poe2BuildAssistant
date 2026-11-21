package com.poe.poe2buildassistant.domain.user;

import java.util.Objects;
import java.util.UUID;

/**
 * Conta de quem entrou com o Google. E-mail e nome são dados pessoais: ficam no banco e na sessão,
 * nunca em logs (só o {@code id}).
 */
public record User(UUID id, String googleSub, String email, String displayName, Role role) {

    public enum Role {
        USER,
        ADMIN
    }

    public User {
        Objects.requireNonNull(id, "usuário sem id");
        if (googleSub == null || googleSub.isBlank()) {
            throw new IllegalArgumentException("usuário sem sub do Google");
        }
        email = email == null ? "" : email.strip();
        displayName = displayName == null || displayName.isBlank() ? "Jogador" : displayName.strip();
        Objects.requireNonNull(role, "usuário sem papel");
    }
}
