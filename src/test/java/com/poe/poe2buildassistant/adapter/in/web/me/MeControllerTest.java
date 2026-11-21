package com.poe.poe2buildassistant.adapter.in.web.me;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUser;
import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUsers;
import com.poe.poe2buildassistant.application.port.in.ListMyBuildsUseCase;
import com.poe.poe2buildassistant.config.SecurityConfig;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Autowired private MockMvc mvc;
    @MockitoBean private ListMyBuildsUseCase listMyBuilds;

    private static SessionUser ana(String role) {
        return SessionUsers.of(ANA, "Ana", role);
    }

    private static Build build(String id, String createdAt) {
        return new Build(
                UUID.fromString(id),
                Optional.of(ANA.toString()),
                1L,
                new BuildRequest("arqueiro mágico", Constraints.none()),
                CatalogFixtures.validDraft(),
                Instant.parse(createdAt));
    }

    @Test
    @DisplayName("GET /api/me com sessão Google devolve nome e papel")
    void eu() throws Exception {
        mvc.perform(get("/api/me").with(oauth2Login().oauth2User(ana("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("GET /api/me não expõe e-mail nem id do Google")
    void semPii() throws Exception {
        mvc.perform(get("/api/me").with(oauth2Login().oauth2User(ana("USER"))))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.sub").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/me sem login responde 401 (não redireciona para o Google)")
    void euSemLogin() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lista só as builds do usuário logado, mais recentes primeiro")
    void minhasBuilds() throws Exception {
        String nova = "00000000-0000-0000-0000-00000000000b";
        String antiga = "00000000-0000-0000-0000-00000000000a";
        when(listMyBuilds.list(ANA.toString()))
                .thenReturn(List.of(build(nova, "2026-11-21T12:00:00Z"), build(antiga, "2026-11-20T12:00:00Z")));

        mvc.perform(get("/api/me/builds").with(oauth2Login().oauth2User(ana("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", Matchers.contains(nova, antiga)))
                .andExpect(jsonPath("$[0].mainSkill").value("Ice Shot"))
                .andExpect(jsonPath("$[0].concept").value("arqueiro mágico"));
    }

    @Test
    @DisplayName("visitante não acessa histórico")
    void historicoSemLogin() throws Exception {
        mvc.perform(get("/api/me/builds")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/admin exige ADMIN: USER recebe 403")
    void adminExigePapel() throws Exception {
        mvc.perform(get("/api/admin/qualquer").with(oauth2Login().oauth2User(ana("USER")))).andExpect(status().isForbidden());
    }
}
