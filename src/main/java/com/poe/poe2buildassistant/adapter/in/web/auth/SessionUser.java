package com.poe.poe2buildassistant.adapter.in.web.auth;

import java.io.Serial;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * Quem está logado, guardado na sessão do servidor (BFF, ADR-001). Carrega o id interno e o papel
 * decididos no login, para que nenhuma requisição precise ler o Google ou o banco de novo. O
 * domínio nunca vê esta classe (ADR-002 § 5).
 */
public class SessionUser extends DefaultOidcUser {

    @Serial private static final long serialVersionUID = 1L;

    private final String userId;
    private final String displayName;
    private final String role;

    public SessionUser(OidcIdToken idToken, String userId, String displayName, String role) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + role)), idToken);
        this.userId = userId;
        this.displayName = displayName;
        this.role = role;
    }

    public String userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }

    public String role() {
        return role;
    }
}
