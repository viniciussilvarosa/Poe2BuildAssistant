package com.poe.poe2buildassistant.adapter.in.web.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

/** Usuário de sessão sintético para testes, igual ao que o login Google cria. */
public final class SessionUsers {

    private SessionUsers() {}

    public static SessionUser of(UUID userId, String name, String role) {
        OidcIdToken token = OidcIdToken.withTokenValue("token-de-teste")
                .subject("g-" + userId)
                .issuedAt(Instant.parse("2026-11-21T00:00:00Z"))
                .expiresAt(Instant.parse("2026-11-21T01:00:00Z"))
                .claim("name", name)
                .build();
        return new SessionUser(token, userId.toString(), name, role);
    }
}
