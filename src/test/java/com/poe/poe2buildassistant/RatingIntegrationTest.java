package com.poe.poe2buildassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /api/builds/{id}/rating com segurança, banco e cota de verdade (card #16). */
@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RatingIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    private UUID build;

    @Autowired private BuildRepositoryPort builds;
    @Autowired private CatalogRepositoryPort catalogs;

    /** Uma build de visitante de verdade, salva pelo mesmo caminho da geração. */
    @BeforeEach
    void build() {
        build = builds.save(new Build(
                        UUID.randomUUID(),
                        Optional.empty(),
                        catalogs.loadPublished().version().id(),
                        new BuildRequest("arqueiro de gelo", Constraints.none()),
                        CatalogFixtures.validDraft(),
                        Instant.parse("2026-11-21T12:00:00Z")))
                .id();
    }

    private ResultActions rate(UUID id, String body) throws Exception {
        return mvc.perform(post("/api/builds/" + id + "/rating").with(csrf())
                .with(r -> {
                    r.setRemoteAddr("203.0.113.9");
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private java.util.List<String> ratings() {
        return jdbc.sql("SELECT requester_key || '=' || value FROM build_rating WHERE build_id = ?").param(build).query(String.class).list();
    }

    @Test
    @DisplayName("POST /api/builds/{id}/rating {value:UP} registra avaliação, com o hash do IP e nunca o IP")
    void registra() throws Exception {
        rate(build, "{\"value\":\"UP\"}").andExpect(status().isNoContent());

        assertThat(ratings()).singleElement().satisfies(r -> {
            assertThat(r).matches("anon:[0-9a-f]{64}=UP");
            assertThat(r).doesNotContain("203.0.113.9");
        });
    }

    @Test
    @DisplayName("segunda avaliação do mesmo avaliador substitui a primeira")
    void substitui() throws Exception {
        rate(build, "{\"value\":\"UP\"}").andExpect(status().isNoContent());
        rate(build, "{\"value\":\"DOWN\"}").andExpect(status().isNoContent());

        assertThat(ratings()).singleElement().satisfies(r -> assertThat(r).endsWith("=DOWN"));
    }

    @Test
    @DisplayName("GET devolve o voto do próprio avaliador para a tela marcar o botão (bug #50)")
    void leVotoAtual() throws Exception {
        rate(build, "{\"value\":\"DOWN\"}").andExpect(status().isNoContent());

        mvc.perform(get("/api/builds/" + build + "/rating").with(r -> {
                    r.setRemoteAddr("203.0.113.9");
                    return r;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("DOWN"));
    }

    @Test
    @DisplayName("GET sem voto, ou o voto de outro avaliador, responde 204 sem expor nada")
    void semVotoProprio() throws Exception {
        rate(build, "{\"value\":\"UP\"}").andExpect(status().isNoContent());

        mvc.perform(get("/api/builds/" + build + "/rating").with(r -> {
                    r.setRemoteAddr("198.51.100.77");
                    return r;
                }))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/builds/" + UUID.randomUUID() + "/rating")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("build inexistente responde 404")
    void inexistente() throws Exception {
        rate(UUID.randomUUID(), "{\"value\":\"UP\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("valor inválido responde 400 em pt-BR, sem falar do campo concept")
    void valorInvalido() throws Exception {
        rate(build, "{\"value\":\"MAYBE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", Matchers.containsString("UP")))
                .andExpect(jsonPath("$.detail", Matchers.not(Matchers.containsString("concept"))));
        assertThat(ratings()).isEmpty();
    }

    @Test
    @DisplayName("sem CSRF é recusado")
    void semCsrf() throws Exception {
        mvc.perform(post("/api/builds/" + build + "/rating").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"UP\"}"))
                .andExpect(status().isForbidden());
    }
}
