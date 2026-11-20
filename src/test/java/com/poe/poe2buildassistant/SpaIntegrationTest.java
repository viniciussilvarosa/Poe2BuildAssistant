package com.poe.poe2buildassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Servidor real servindo o build do React (o Maven copia frontend/dist para static/ antes dos
 * testes). Prova a integração ponta a ponta: jar → index.html → rotas do SPA.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SpaIntegrationTest {

    @LocalServerPort private int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("GET / serve o index.html do SPA")
    void raizServeSpa() throws Exception {
        HttpResponse<String> r = get("/");

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("<div id=\"root\"></div>").contains("lang=\"pt-BR\"");
    }

    @ParameterizedTest(name = "rota do SPA {0} faz fallback para index.html")
    @ValueSource(strings = {"/criar", "/gerando", "/builds/00000000-0000-0000-0000-000000000042", "/minhas-builds", "/admin"})
    void rotasDoSpa(String path) throws Exception {
        HttpResponse<String> r = get(path);

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("<div id=\"root\"></div>");
    }

    @Test
    @DisplayName("assets gerados pelo Vite são servidos")
    void assetsServidos() throws Exception {
        String index = get("/").body();
        String script = index.replaceAll("(?s).*<script type=\"module\" crossorigin src=\"([^\"]+)\".*", "$1");

        assertThat(script).startsWith("/assets/");
        assertThat(get(script).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("o fallback do SPA nunca captura /api/**: 404 continua 404, sem HTML")
    void apiNaoCaiNoSpa() throws Exception {
        HttpResponse<String> r = get("/api/nao-existe");

        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).doesNotContain("<div id=\"root\">");
    }

    @Test
    @DisplayName("rota fora da lista do SPA continua negada")
    void rotaForaDaListaNegada() throws Exception {
        assertThat(get("/qualquer-coisa").statusCode()).isEqualTo(403);
    }
}
