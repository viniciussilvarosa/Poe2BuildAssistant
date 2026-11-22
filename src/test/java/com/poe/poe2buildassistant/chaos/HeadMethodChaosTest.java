package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Caos do PR #91 (#65): HEAD liberado nas páginas públicas. Servidor real, nenhuma chamada externa. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class HeadMethodChaosTest {

    @LocalServerPort private int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (headers.length > 0) {
            b.headers(headers);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Nested
    @DisplayName("H1: HEAD não revela arquivos internos")
    class ArquivosInternos {

        @ParameterizedTest(name = "HEAD {0} continua 4xx")
        @ValueSource(strings = {
            "/application.properties",
            "/application-prod.properties",
            "/db/migration/V1__baseline.sql",
            "/assets/../application.properties",
            "/assets/%2e%2e/application.properties",
            "/META-INF/spring.factories",
            "/prompts/build-system.st",
            "/com/poe/poe2buildassistant/config/SecurityConfig.class"
        })
        void naoExpoe(String path) throws Exception {
            HttpResponse<String> r = send("HEAD", path);

            assertThat(r.statusCode()).as("status de HEAD %s", path).isBetween(400, 499);
            assertThat(r.body()).isEmpty();
        }
    }

    @Nested
    @DisplayName("H2: variações de caminho não abrem a API protegida por HEAD")
    class ApiProtegida {

        @ParameterizedTest(name = "HEAD {0} igual ao GET e nunca 200")
        @ValueSource(strings = {
            "/api/me/",
            "/api/./me",
            "/api/me;x=1",
            "/API/ME",
            "/api/admin/catalog/drafts/current/",
            "/api/admin/../admin/catalog/drafts/current",
            "/api/admin/catalog/drafts/current;jsessionid=x",
            "/api/admin/catalog/drafts/current%2F"
        })
        void mesmoStatusDoGet(String path) throws Exception {
            int head = send("HEAD", path).statusCode();

            assertThat(head).as("HEAD %s", path).isNotEqualTo(200).isEqualTo(send("GET", path).statusCode());
        }
    }

    @Nested
    @DisplayName("H3: cabeçalhos de segurança também no HEAD")
    class Cabecalhos {

        // KILLED no PR #91 antes da correção: em / e nas rotas do SPA (forward para o index.html), o HEAD
        // saía sem nenhum cabeçalho do Spring Security, que só os escrevia no commit da resposta.
        @ParameterizedTest(name = "HEAD {0} sai com CSP, X-Frame-Options, nosniff e no-store")
        @ValueSource(strings = {"/", "/criar", "/qualquer-coisa"})
        void cabecalhos(String path) throws Exception {
            var h = send("HEAD", path).headers();

            assertThat(h.firstValue("Content-Security-Policy")).hasValueSatisfying(csp -> assertThat(csp).contains("default-src 'self'"));
            assertThat(h.firstValue("X-Frame-Options")).contains("DENY");
            assertThat(h.firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(h.firstValue("Cache-Control")).hasValueSatisfying(cc -> assertThat(cc).contains("no-store"));
        }
    }

    @Nested
    @DisplayName("H3b: cabeçalhos escritos cedo não estragam o cache")
    class CacheIntacto {

        @ParameterizedTest(name = "{0} no asset do Vite: cache longo, sem Pragma no-cache nem Expires 0")
        @ValueSource(strings = {"GET", "HEAD"})
        void assetContinuaCacheavel(String method) throws Exception {
            String index = send("GET", "/").body();
            String script = index.replaceAll("(?s).*<script type=\"module\" crossorigin src=\"([^\"]+)\".*", "$1");

            var h = send(method, script).headers();

            assertThat(h.firstValue("Cache-Control")).hasValueSatisfying(cc -> assertThat(cc).contains("max-age=31536000").doesNotContain("no-store"));
            assertThat(h.firstValue("Pragma").orElse("")).doesNotContain("no-cache");
            assertThat(h.firstValue("Expires").orElse("")).isNotEqualTo("0");
            assertThat(h.firstValue("X-Content-Type-Options")).contains("nosniff");
        }
    }

    @Nested
    @DisplayName("H4: HEAD anônimo não cria sessão (chaos K4)")
    class Sessao {

        @ParameterizedTest(name = "HEAD {0} sem JSESSIONID")
        @ValueSource(strings = {"/", "/criar", "/qualquer-coisa", "/favicon.svg"})
        void semSessao(String path) throws Exception {
            assertThat(send("HEAD", path).headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID="));
        }
    }

    @Nested
    @DisplayName("H5: só GET e HEAD foram liberados fora da API")
    class OutrosMetodos {

        @ParameterizedTest(name = "{0} / continua negado")
        @ValueSource(strings = {"OPTIONS", "PUT", "DELETE", "PATCH", "TRACE", "PROPFIND"})
        void negado(String method) throws Exception {
            assertThat(send(method, "/").statusCode()).as(method).isBetween(400, 499);
            assertThat(send(method, "/criar").statusCode()).as(method).isBetween(400, 499);
        }

        @Test
        @DisplayName("método em minúsculas ('head') não é tratado como HEAD liberado")
        void metodoMinusculo() throws Exception {
            assertThat(send("head", "/").statusCode()).isBetween(400, 499);
        }
    }

    @Nested
    @DisplayName("H6: sobrescrita de método não transforma POST em HEAD")
    class Sobrescrita {

        @Test
        @DisplayName("POST /criar?_method=HEAD continua 403")
        void parametro() throws Exception {
            assertThat(send("POST", "/criar?_method=HEAD").statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("POST /criar com X-HTTP-Method-Override: HEAD continua 403")
        void cabecalho() throws Exception {
            assertThat(send("POST", "/criar", "X-HTTP-Method-Override", "HEAD").statusCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("H7: HEAD em rotas do Spring Security")
    class RotasDoSpringSecurity {

        @Test
        @DisplayName("HEAD /logout não é logout (só POST com CSRF desloga)")
        void logout() throws Exception {
            assertThat(send("HEAD", "/logout").statusCode()).isNotEqualTo(204);
        }

        @Test
        @DisplayName("HEAD /error responde como o GET, sem corpo")
        void erro() throws Exception {
            HttpResponse<String> r = send("HEAD", "/error");

            assertThat(r.statusCode()).isEqualTo(send("GET", "/error").statusCode());
            assertThat(r.body()).isEmpty();
        }
    }
}
