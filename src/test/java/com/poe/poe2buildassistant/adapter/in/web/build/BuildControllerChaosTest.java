package com.poe.poe2buildassistant.adapter.in.web.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase;
import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase.GenerateBuildCommand;
import com.poe.poe2buildassistant.application.port.in.GetBuildUseCase;
import com.poe.poe2buildassistant.config.SecurityConfig;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ataques à fronteira pública da API de geração (card #11). */
@WebMvcTest(BuildController.class)
@Import({SecurityConfig.class, BuildControllerTest.WebTestConfig.class})
class BuildControllerChaosTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private GenerateBuildUseCase generateBuild;
    @MockitoBean private GetBuildUseCase getBuild;

    private static MockHttpServletRequestBuilder generate(String contentType, String body) {
        return post("/api/builds/generate").with(csrf()).contentType(contentType).content(body);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result.getAsyncResult(5_000);
        }
        return result.getResponse();
    }

    @Nested
    @DisplayName("W1/W2/W14/W16 — corpo hostil é recusado com 400/415 em pt-BR, nunca 500")
    class CorpoHostil {

        @ParameterizedTest(name = "corpo \"{0}\" → 400")
        @ValueSource(strings = {"", "null", "[]", "{\"concept\": [\"a\"]}", "{\"concept\": \"x\", \"characterClass\": {}}"})
        void corpoInvalido(String body) throws Exception {
            MockHttpServletResponse r = send(generate(MediaType.APPLICATION_JSON_VALUE, body));

            assertThat(r.getStatus()).isEqualTo(400);
            assertThat(r.getContentAsString(StandardCharsets.UTF_8)).contains("\"detail\"");
            verify(generateBuild, never()).generate(any(), any());
        }

        @Test
        @DisplayName("W14: Content-Type text/plain → 415")
        void contentTypeErrado() throws Exception {
            assertThat(send(generate(MediaType.TEXT_PLAIN_VALUE, "arqueiro")).getStatus()).isEqualTo(415);
        }

        @Test
        @DisplayName("W16: JSON malformado responde com mensagem em pt-BR")
        void jsonMalformadoEmPortugues() throws Exception {
            MockHttpServletResponse r = send(generate(MediaType.APPLICATION_JSON_VALUE, "{\"concept\": \"corta aqui"));

            assertThat(r.getStatus()).isEqualTo(400);
            assertThat(r.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Failed to read").contains("inválid");
        }
    }

    @Nested
    @DisplayName("W5 — seletores gigantes são recusados antes do stream")
    class SeletorGigante {

        @ParameterizedTest(name = "{0} com 10 mil caracteres → 400 sem eco")
        @ValueSource(strings = {"characterClass", "ascendancy", "weaponType", "mainSkill", "playstyle"})
        void seletorGigante(String campo) throws Exception {
            String gigante = "X".repeat(10_000);
            MockHttpServletResponse r = send(generate(
                    MediaType.APPLICATION_JSON_VALUE, "{\"concept\":\"arqueiro\",\"" + campo + "\":\"" + gigante + "\"}"));

            assertThat(r.getStatus()).isEqualTo(400);
            assertThat(r.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(gigante);
            verify(generateBuild, never()).generate(any(), any());
        }
    }

    @Nested
    @DisplayName("W7/W17 — identidade do cliente")
    class IdentidadeDoCliente {

        @Test
        @DisplayName("W7: fora de prod, X-Forwarded-For forjado é ignorado")
        void xffIgnorado() throws Exception {
            when(generateBuild.generate(any(), any())).thenThrow(new IllegalStateException("parar aqui"));

            send(generate(MediaType.APPLICATION_JSON_VALUE, "{\"concept\":\"x\"}")
                    .header("X-Forwarded-For", "198.51.100.99")
                    .with(r -> {
                        r.setRemoteAddr("203.0.113.7");
                        return r;
                    }));

            ArgumentCaptor<GenerateBuildCommand> captor = ArgumentCaptor.forClass(GenerateBuildCommand.class);
            verify(generateBuild).generate(captor.capture(), any());
            assertThat(captor.getValue().requester())
                    .isEqualTo(Requester.anonymousFromIp("203.0.113.7", "salt-de-teste"));
        }

        @ParameterizedTest(name = "W17: remoteAddr \"{0}\" nunca vira 500")
        @ValueSource(strings = {"unknown", "fe80::1%eth0", ""})
        void remoteAddrEstranho(String addr) throws Exception {
            MockHttpServletResponse r = send(generate(MediaType.APPLICATION_JSON_VALUE, "{\"concept\":\"x\"}").with(req -> {
                req.setRemoteAddr(addr);
                return req;
            }));

            assertThat(r.getStatus()).isLessThan(500);
        }
    }

    @Nested
    @DisplayName("W8 — erro inesperado não vaza detalhes internos")
    class SemVazamento {

        @Test
        @DisplayName("mensagem interna de uma exceção inesperada não aparece no evento error")
        void semDetalheInterno() throws Exception {
            when(generateBuild.generate(any(), any()))
                    .thenThrow(new IllegalStateException("SELECT * FROM app_user WHERE token='abc' — pool exhausted"));

            String stream = send(generate(MediaType.APPLICATION_JSON_VALUE, "{\"concept\":\"x\"}"))
                    .getContentAsString(StandardCharsets.UTF_8);

            assertThat(stream).contains("event:error").contains("\"status\":500")
                    .doesNotContain("SELECT").doesNotContain("app_user").doesNotContain("pool exhausted");
        }
    }
}
