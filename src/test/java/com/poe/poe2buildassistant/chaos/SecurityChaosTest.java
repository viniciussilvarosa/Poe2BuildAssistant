package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Ataques à configuração de segurança base (card #1). */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityChaosTest {

    @Autowired private MockMvc mvc;

    @Nested
    @DisplayName("A1 — caminhos hostis contra /api/admin/**")
    class CaminhosHostis {

        @ParameterizedTest(name = "GET {0} nunca é atendido")
        @ValueSource(
                strings = {
                    "/api/admin",
                    "/api/admin/",
                    "/api/admin/catalog",
                    "/API/admin/catalog",
                    "/api/ADMIN/catalog",
                    "/api/admin/../admin/catalog",
                    "/api//admin/catalog",
                    "/api/admin;x=1/catalog",
                    "/api/%61dmin/catalog",
                    "/api/admin%2Fcatalog"
                })
        void adminNuncaAlcancavel(String path) throws Exception {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as("status para %s (nunca 2xx)", path).isIn(400, 403, 404);
        }
    }

    @Nested
    @DisplayName("A2 — métodos que alteram estado sem CSRF")
    class MetodosSemCsrf {

        static Stream<MockHttpServletRequestBuilder> metodos() {
            return Stream.of(put("/api/health"), patch("/api/health"), delete("/api/health"));
        }

        @ParameterizedTest(name = "{0} sem token é 403")
        @MethodSource("metodos")
        void rejeitaSemToken(MockHttpServletRequestBuilder request) throws Exception {
            mvc.perform(request).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("A3/A4 — fluxo real do SPA com cookie XSRF-TOKEN")
    class FluxoSpa {

        private Cookie obterCookieCsrf() throws Exception {
            MvcResult r = mvc.perform(get("/api/health")).andReturn();
            Cookie c = r.getResponse().getCookie("XSRF-TOKEN");
            assertThat(c).isNotNull();
            return c;
        }

        @Test
        @DisplayName("A3: POST com o valor do cookie no header X-XSRF-TOKEN passa pelo filtro")
        void idaEVoltaDoSpaFunciona() throws Exception {
            Cookie cookie = obterCookieCsrf();
            mvc.perform(post("/api/health").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("A4: header adulterado é rejeitado")
        void tokenAdulteradoRejeitado() throws Exception {
            Cookie cookie = obterCookieCsrf();
            mvc.perform(post("/api/health").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue() + "x"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("A4: token de outro cookie (outra sessão) é rejeitado")
        void tokenDeOutroCookieRejeitado() throws Exception {
            Cookie meu = obterCookieCsrf();
            Cookie deOutro = obterCookieCsrf();
            assertThat(meu.getValue()).isNotEqualTo(deOutro.getValue());
            mvc.perform(post("/api/health").cookie(meu).header("X-XSRF-TOKEN", deOutro.getValue()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("A4: header sem cookie é rejeitado")
        void headerSemCookieRejeitado() throws Exception {
            mvc.perform(post("/api/health").header("X-XSRF-TOKEN", "qualquer"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("A5 — requisições anônimas não criam sessão")
    class SemSessao {

        @ParameterizedTest(name = "GET {0} não cria sessão")
        @ValueSource(strings = {"/api/health", "/api/admin/catalog", "/qualquer-rota", "/"})
        void naoCriaSessao(String path) throws Exception {
            MvcResult r = mvc.perform(get(path)).andReturn();
            assertThat(r.getRequest().getSession(false)).as("sessão criada em %s", path).isNull();
        }

        @Test
        @DisplayName("POST negado por CSRF não cria sessão")
        void postNegadoNaoCriaSessao() throws Exception {
            MvcResult r = mvc.perform(post("/api/health")).andReturn();
            assertThat(r.getRequest().getSession(false)).isNull();
        }

        @Test
        @DisplayName("controle: com token válido não há sessão")
        void comTokenNaoCriaSessao() throws Exception {
            MvcResult r = mvc.perform(post("/api/health").with(csrf())).andReturn();
            assertThat(r.getRequest().getSession(false)).isNull();
        }
    }
}
