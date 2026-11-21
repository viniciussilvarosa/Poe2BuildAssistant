package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUsers;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Ataques ao login Google e à sessão (card #14, PR #36), com o contexto inteiro. */
@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthChaosTest {

    @Autowired private MockMvc mvc;

    private MockHttpServletResponse send(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    @Test
    @DisplayName("L1 — o link Entrar leva ao Google, mesmo com 'qualquer outra rota negada'")
    void entrarLevaAoGoogle() throws Exception {
        MockHttpServletResponse r = send(get("/oauth2/authorization/google"));

        assertThat(r.getStatus()).isEqualTo(302);
        assertThat(r.getRedirectedUrl()).startsWith("https://accounts.google.com/");
    }

    @Test
    @DisplayName("L2 — callback do Google forjado (sem pedido de autorização) volta ao SPA com erro, sem 500")
    void callbackForjado() throws Exception {
        MockHttpServletResponse r = send(get("/login/oauth2/code/google").param("code", "x").param("state", "y"));

        assertThat(r.getStatus()).isEqualTo(302);
        assertThat(r.getRedirectedUrl()).isEqualTo("/?login=falhou");
    }

    @Test
    @DisplayName("L3 — logout sem CSRF é recusado (um site de terceiros não desloga ninguém)")
    void logoutSemCsrf() throws Exception {
        MockHttpServletResponse r = send(post("/logout").with(oauth2Login().oauth2User(SessionUsers.of(UUID.randomUUID(), "Ana", "USER"))));

        assertThat(r.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("L4 — GET /logout não desloga")
    void logoutPorGet() throws Exception {
        MockHttpServletResponse r = send(get("/logout").with(oauth2Login().oauth2User(SessionUsers.of(UUID.randomUUID(), "Ana", "USER"))));

        assertThat(r.getStatus()).isNotEqualTo(204);
    }

    @Test
    @DisplayName("L5 — sessão OAuth2 sem SessionUser (ex.: escopo sem openid) responde 401, não 500")
    void principalInesperado() throws Exception {
        MockHttpServletResponse r = send(get("/api/me").with(oauth2Login()));

        assertThat(r.getStatus()).isEqualTo(401);
    }
}
