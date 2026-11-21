package com.poe.poe2buildassistant.domain.user;

import java.util.Objects;
import java.util.UUID;

/**
 * Conta de quem entrou com o Google. E-mail e nome são dados pessoais: ficam no banco e na sessão,
 * nunca em logs (só o {@code id}).
 */
public record User(UUID id, String googleSub, String email, String displayName, Role role) {

    public static final int MAX_DISPLAY_NAME = 200;
    public static final int MAX_EMAIL = 320;

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    public enum Role {
        USER,
        ADMIN
    }

    public User {
        Objects.requireNonNull(id, "usuário sem id");
        if (googleSub == null || googleSub.isBlank()) {
            throw new IllegalArgumentException("usuário sem sub do Google");
        }
        // Os limites das colunas: um nome longo do Google não pode impedir o login (chaos L7).
        email = cut(email == null ? "" : email.strip(), MAX_EMAIL);
        displayName = displayName == null || displayName.isBlank() ? "Jogador" : cut(displayName.strip(), MAX_DISPLAY_NAME);
        Objects.requireNonNull(role, "usuário sem papel");
    }
}
