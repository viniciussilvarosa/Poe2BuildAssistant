package com.poe.poe2buildassistant.adapter.in.web.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.adapter.in.web.RequesterResolver;
import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase;
import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase.GenerateBuildCommand;
import com.poe.poe2buildassistant.application.port.in.GetBuildUseCase;
import com.poe.poe2buildassistant.application.port.out.GenerationProgressPort;
import com.poe.poe2buildassistant.config.SecurityConfig;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GenerationStep;
import com.poe.poe2buildassistant.domain.quota.Requester;
import com.poe.poe2buildassistant.domain.shared.GenerationFailed;
import com.poe.poe2buildassistant.domain.shared.ProviderUnavailable;
import com.poe.poe2buildassistant.domain.shared.QuotaExceeded;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(BuildController.class)
@Import({SecurityConfig.class, BuildControllerTest.WebTestConfig.class})
class BuildControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");
    private static final String SALT = "salt-de-teste";

    @TestConfiguration
    static class WebTestConfig {
        @Bean
        RequesterResolver requesterResolver() {
            return new RequesterResolver(SALT);
        }

        /** Executor síncrono: o stream inteiro é produzido antes do controller retornar. */
        @Bean
        GenerationStreamTimeout generationStreamTimeout() {
            return new GenerationStreamTimeout(java.time.Duration.ofSeconds(90));
        }

        @Bean(BuildController.EXECUTOR)
        Executor buildGenerationExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private GenerateBuildUseCase generateBuild;
    @MockitoBean private GetBuildUseCase getBuild;

    private static Build build() {
        return new Build(
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                Optional.empty(),
                1L,
                new BuildRequest("arqueiro mágico", Constraints.none().withCharacterClass("Ranger")),
                CatalogFixtures.validDraft(),
                NOW);
    }

    private String stream(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/builds/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        result.getAsyncResult(5_000);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        // EventSource sempre decodifica text/event-stream como UTF-8 (especificação SSE).
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<String> events(String stream, String name) {
        Matcher m = Pattern.compile("event:" + name + "\\ndata:(.*)").matcher(stream);
        List<String> data = new java.util.ArrayList<>();
        while (m.find()) {
            data.add(m.group(1));
        }
        return data;
    }

    @Test
    @DisplayName("stream emite progress por etapa e done com a build")
    void streamComProgressoEDone() throws Exception {
        when(generateBuild.generate(any(), any())).thenAnswer(inv -> {
            GenerationProgressPort progress = inv.getArgument(1);
            for (GenerationStep step : GenerationStep.values()) {
                progress.emit(step);
            }
            return build();
        });

        String stream = stream("{\"concept\":\"arqueiro mágico\"}");

        List<String> progress = events(stream, "progress");
        assertThat(progress).hasSize(5);
        assertThat(progress.getFirst()).contains("\"step\":\"VALIDATING_INPUT\"").contains("\"percent\":10");
        assertThat(progress.getLast()).contains("\"percent\":100");
        assertThat(events(stream, "done")).singleElement().asString()
                .contains("\"id\":\"00000000-0000-0000-0000-000000000042\"")
                .contains("\"mainSkill\":\"Ice Shot\"");
    }

    @Test
    @DisplayName("percentual nunca volta atrás numa regeneração")
    void percentualMonotonico() throws Exception {
        when(generateBuild.generate(any(), any())).thenAnswer(inv -> {
            GenerationProgressPort p = inv.getArgument(1);
            p.emit(GenerationStep.VALIDATING_INPUT);
            p.emit(GenerationStep.GENERATING);
            p.emit(GenerationStep.VALIDATING_OUTPUT);
            p.emit(GenerationStep.GENERATING); // regeneração
            p.emit(GenerationStep.VALIDATING_OUTPUT);
            return build();
        });

        List<Integer> percents = events(stream("{\"concept\":\"x\"}"), "progress").stream()
                .map(d -> Integer.parseInt(d.replaceAll(".*\"percent\":(\\d+).*", "$1")))
                .toList();

        assertThat(percents).isSorted();
    }

    @Test
    @DisplayName("conceito vazio responde 400 antes de abrir o stream")
    void conceitoVazio() throws Exception {
        mvc.perform(post("/api/builds/generate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"concept\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("conceito")));
        verify(generateBuild, never()).generate(any(), any());
    }

    @Test
    @DisplayName("conceito com mais de 500 caracteres responde 400 antes de abrir o stream")
    void conceitoLongo() throws Exception {
        mvc.perform(post("/api/builds/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concept\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(generateBuild, never()).generate(any(), any());
    }

    @Test
    @DisplayName("cota esgotada: evento error com status 429 e retryAt")
    void cotaEsgotada() throws Exception {
        when(generateBuild.generate(any(), any())).thenThrow(new QuotaExceeded(Instant.parse("2026-11-22T00:00:00Z")));

        assertThat(events(stream("{\"concept\":\"x\"}"), "error")).singleElement().asString()
                .contains("\"status\":429")
                .contains("\"retryAt\":\"2026-11-22T00:00:00Z\"")
                .contains("limite diário");
    }

    @Test
    @DisplayName("provedor indisponível: evento error 503 com retryAt quando houver")
    void provedorIndisponivel() throws Exception {
        when(generateBuild.generate(any(), any())).thenThrow(new ProviderUnavailable(Optional.of(NOW.plusSeconds(60))));

        assertThat(events(stream("{\"concept\":\"x\"}"), "error")).singleElement().asString()
                .contains("\"status\":503")
                .contains("Tente novamente")
                .contains("\"retryAt\":\"2026-11-21T15:01:00Z\"");
    }

    @Test
    @DisplayName("geração sem build válida: evento error 502")
    void geracaoFalhou() throws Exception {
        when(generateBuild.generate(any(), any())).thenThrow(new GenerationFailed());

        assertThat(events(stream("{\"concept\":\"x\"}"), "error")).singleElement().asString().contains("\"status\":502");
    }

    @Test
    @DisplayName("requester anônimo é resolvido a partir do IP do cliente, só como hash")
    void requesterPorIp() throws Exception {
        when(generateBuild.generate(any(), any())).thenReturn(build());

        mvc.perform(post("/api/builds/generate")
                        .with(csrf())
                        .with(r -> {
                            r.setRemoteAddr("203.0.113.7");
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concept\":\"arqueiro\",\"characterClass\":\"Ranger\",\"weaponType\":\" \"}"))
                .andReturn()
                .getAsyncResult(5_000);

        ArgumentCaptor<GenerateBuildCommand> captor = ArgumentCaptor.forClass(GenerateBuildCommand.class);
        verify(generateBuild).generate(captor.capture(), any());
        GenerateBuildCommand command = captor.getValue();
        assertThat(command.requester()).isEqualTo(Requester.anonymousFromIp("203.0.113.7", SALT));
        assertThat(command.request().constraints().characterClass()).contains("Ranger");
        assertThat(command.request().constraints().weaponType()).isEmpty();
    }

    @Test
    @DisplayName("POST sem CSRF é rejeitado")
    void semCsrf() throws Exception {
        mvc.perform(post("/api/builds/generate").contentType(MediaType.APPLICATION_JSON).content("{\"concept\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/builds/{id} devolve build existente, sem expor o dono")
    void getBuild() throws Exception {
        Build b = new Build(build().id(), Optional.of("42"), 1L, build().request(), build().content(), NOW);
        when(getBuild.get(b.id())).thenReturn(Optional.of(b));

        mvc.perform(get("/api/builds/{id}", b.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.mainSkill").value("Ice Shot"))
                .andExpect(jsonPath("$.request.concept").value("arqueiro mágico"))
                .andExpect(jsonPath("$.request.characterClass").value("Ranger"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    @DisplayName("id inexistente responde 404 ProblemDetail")
    void getBuildInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(getBuild.get(id)).thenReturn(Optional.empty());

        mvc.perform(get("/api/builds/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("id malformado responde 400, não 500")
    void idMalformado() throws Exception {
        mvc.perform(get("/api/builds/{id}", "nao-e-uuid")).andExpect(status().isBadRequest());
    }
}
