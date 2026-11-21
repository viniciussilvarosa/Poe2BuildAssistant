package com.poe.poe2buildassistant;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUsers;
import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase;
import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase.GenerateBuildCommand;
import com.poe.poe2buildassistant.application.port.out.BuildGeneratorPort;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.GenerationProgressPort;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Geração logada → "Minhas builds", com o catálogo real do seed e o LLM simulado (card #14). */
@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MyBuildsIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private GenerateBuildUseCase generateBuild;
    @Autowired private CatalogRepositoryPort catalogs;
    @MockitoBean private BuildGeneratorPort generator;

    private Build generateAs(Requester requester) {
        List<String> notables = catalogs.loadPublished().notables().stream().limit(5).map(n -> n.name()).toList();
        when(generator.generate(any(), any(), any())).thenReturn(new GeneratedBuildDraft(
                "Arqueiro de gelo.",
                "Ice Shot congela e Pierce atravessa.",
                List.of(),
                "Ranger",
                "Deadeye",
                "Bow",
                "Ice Shot",
                List.of("Pierce I"),
                new GeneratedBuildDraft.PassiveDirection("Lado da Ranger.", notables),
                new GeneratedBuildDraft.GearPriorities(List.of(), List.of(), List.of()),
                List.of()));
        return generateBuild.generate(
                new GenerateBuildCommand(new BuildRequest("arqueiro de gelo", Constraints.none()), requester),
                GenerationProgressPort.none());
    }

    @Test
    @DisplayName("build gerada logado aparece em /api/me/builds; a de visitante não")
    void buildLogadaNoHistorico() throws Exception {
        UUID userId = UUID.randomUUID();
        Build minha = generateAs(new Requester.Authenticated(userId.toString()));
        Build deVisitante = generateAs(new Requester.Anonymous("a".repeat(64)));

        mvc.perform(get("/api/me/builds").with(oauth2Login().oauth2User(SessionUsers.of(userId, "Ana", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(minha.id().toString())))
                .andExpect(jsonPath("$[*].id", not(hasItem(deVisitante.id().toString()))));
    }
}
