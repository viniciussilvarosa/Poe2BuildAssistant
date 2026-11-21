package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Ataques à avaliação 👍/👎 (card #16, PR #42), com o contexto inteiro. */
@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RatingChaosTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private BuildRepositoryPort builds;
    @Autowired private CatalogRepositoryPort catalogs;
    private UUID build;

    @BeforeEach
    void build() {
        build = builds.save(new Build(UUID.randomUUID(), Optional.empty(), catalogs.loadPublished().version().id(),
                        new BuildRequest("arqueiro de gelo", Constraints.none()), CatalogFixtures.validDraft(), Instant.now()))
                .id();
    }

    private MockHttpServletResponse rate(String path, String body) throws Exception {
        return mvc.perform(post(path).with(csrf()).with(r -> {
                            r.setRemoteAddr("203.0.113.50");
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();
    }

    private int ratings() {
        return jdbc.sql("SELECT count(*) FROM build_rating WHERE build_id = ?").param(build).query(Integer.class).single();
    }

    @Test
    @DisplayName("R1 — corpo sem value responde 400 em pt-BR")
    void semValor() throws Exception {
        MockHttpServletResponse r = rate("/api/builds/" + build + "/rating", "{}");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("UP ou DOWN");
        assertThat(ratings()).isZero();
    }

    @Test
    @DisplayName("R2 — value de 10 mil caracteres é recusado sem gravar nada")
    void valorGigante() throws Exception {
        MockHttpServletResponse r = rate("/api/builds/" + build + "/rating", "{\"value\":\"" + "U".repeat(10_000) + "\"}");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(ratings()).isZero();
    }

    @Test
    @DisplayName("R3 — id que não é UUID responde 400 em pt-BR, sem detalhe interno de conversão")
    void idInvalido() throws Exception {
        MockHttpServletResponse r = rate("/api/builds/nao-e-uuid/rating", "{\"value\":\"UP\"}");

        assertThat(r.getStatus()).isEqualTo(400);
        String body = r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("java.util.UUID").doesNotContain("Failed to convert");
    }

    @Test
    @DisplayName("R4 — 20 votos simultâneos do mesmo avaliador ficam em uma linha, sem 500")
    void votosConcorrentes() throws Exception {
        String path = "/api/builds/" + build + "/rating";
        CyclicBarrier juntos = new CyclicBarrier(20);
        Callable<Integer> voto = () -> {
            juntos.await();
            return rate(path, "{\"value\":\"UP\"}").getStatus();
        };

        try (var pool = Executors.newFixedThreadPool(20)) {
            for (Future<Integer> f : pool.invokeAll(java.util.Collections.nCopies(20, voto))) {
                assertThat(f.get()).isEqualTo(204);
            }
        }
        assertThat(ratings()).isEqualTo(1);
    }
}
