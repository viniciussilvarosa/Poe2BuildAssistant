package com.poe.poe2buildassistant.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUser;
import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUsers;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

class RequesterResolverTest {

    private final RequesterResolver resolver = new RequesterResolver("salt-de-teste");

    @Test
    @DisplayName("com sessão resolve Authenticated(userId); sem sessão Anonymous(ipHash)")
    void resolveOsDoisTipos() {
        UUID userId = UUID.randomUUID();
        SessionUser user = SessionUsers.of(userId, "Ana", "USER");
        MockHttpServletRequest logado = new MockHttpServletRequest();
        logado.setRemoteAddr("203.0.113.7");
        logado.setUserPrincipal(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
        MockHttpServletRequest visitante = new MockHttpServletRequest();
        visitante.setRemoteAddr("203.0.113.7");

        assertThat(resolver.resolve(logado)).isEqualTo(new Requester.Authenticated(userId.toString()));
        assertThat(resolver.resolve(visitante)).isInstanceOf(Requester.Anonymous.class);
    }
}
