package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Ataques ao SPA servido pelo Spring (card #2, PR da fundação): servidor real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SpaChaosTest {

    @LocalServerPort private int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String rawPath) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String asset(String index, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(index);
        assertThat(m.find()).as("asset no index.html: %s", pattern).isTrue();
        return m.group(1);
    }

    @Nested
    @DisplayName("F1/F2 — nenhum arquivo interno é servido")
    class ArquivosInternos {

        @ParameterizedTest(name = "{0} não devolve configuração nem SQL")
        @ValueSource(strings = {
            "/application.properties",
            "/db/migration/V1__baseline.sql",
            "/assets/../application.properties",
            "/assets/%2e%2e/application.properties",
            "/assets/..%2fapplication.properties",
            "/static/index.html",
            "/META-INF/spring.factories",
            "/prompts/build-system.st"
        })
        void naoExpoe(String path) throws Exception {
            HttpResponse<String> r = get(path);

            assertThat(r.statusCode()).as("status de %s", path).isBetween(400, 499);
            assertThat(r.body()).doesNotContain("spring.datasource").doesNotContain("spring.ai").doesNotContain("SELECT 1");
        }

        @Test
        @DisplayName("F6: asset inexistente responde 404, não o index.html")
        void assetInexistente() throws Exception {
            HttpResponse<String> r = get("/assets/nao-existe.js");

            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(r.body()).doesNotContain("<div id=\"root\">");
        }
    }

    @Nested
    @DisplayName("F5 — cabeçalhos do HTML e dos assets")
    class Cabecalhos {

        @Test
        @DisplayName("F5a: o HTML sai com Content-Security-Policy restritiva (só a própria origem)")
        void cspNoHtml() throws Exception {
            String csp = get("/").headers().firstValue("Content-Security-Policy").orElse("");

            assertThat(csp).contains("default-src 'self'").contains("frame-ancestors 'none'").doesNotContain("unsafe-eval");
        }

        @Test
        @DisplayName("F5c: HTML com X-Frame-Options e nosniff")
        void cabecalhosBasicos() throws Exception {
            var headers = get("/").headers();

            assertThat(headers.firstValue("X-Frame-Options")).contains("DENY");
            assertThat(headers.firstValue("X-Content-Type-Options")).contains("nosniff");
        }

        @Test
        @DisplayName("F5b: assets com hash no nome saem com cache longo e imutável")
        void cacheDosAssets() throws Exception {
            String script = asset(get("/").body(), "src=\"(/assets/[^\"]+\\.js)\"");

            String cache = get(script).headers().firstValue("Cache-Control").orElse("");

            assertThat(cache).contains("max-age=31536000").contains("immutable").doesNotContain("no-store");
        }
    }

    @Nested
    @DisplayName("F20 — privacidade: fontes só da própria origem")
    class FontesLocais {

        @Test
        @DisplayName("o CSS do build não chama o Google Fonts e as fontes vêm de /assets")
        void semGoogleFonts() throws Exception {
            String css = get(asset(get("/").body(), "href=\"(/assets/[^\"]+\\.css)\"")).body();

            assertThat(css).doesNotContain("googleapis").doesNotContain("gstatic").contains("@font-face");
            Matcher urls = Pattern.compile("url\\(([^)]+)\\)").matcher(css);
            while (urls.find()) {
                assertThat(urls.group(1)).as("fonte servida pela própria origem").startsWith("/assets/");
            }
        }
    }
}
