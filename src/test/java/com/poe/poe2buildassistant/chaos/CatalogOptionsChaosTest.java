package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.application.service.GetCatalogOptionsService;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Ataques a GET /api/catalog/options (card #7, PR #30). */
class CatalogOptionsChaosTest {

    private static final String PATH = "/api/catalog/options";

    static HttpResponse<String> send(int port, String method, String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                                .method(method, HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.ai.google.genai.api-key=test-key-ficticia")
    @ActiveProfiles("test")
    @Import(TestcontainersConfiguration.class)
    @DisplayName("O1–O5 — servidor real com Postgres")
    class ServidorReal {

        @LocalServerPort private int port;
        @Autowired private JdbcClient jdbc;

        /** A carga inicial (V5) já publica o patch 0.5; cada ataque monta o próprio catálogo. */
        @BeforeEach
        @AfterEach
        void limpar() {
            jdbc.sql("DELETE FROM catalog_version").update();
        }

        private long version(String patch, String status) {
            return jdbc.sql("INSERT INTO catalog_version (patch, status) VALUES (?, ?) RETURNING id")
                    .params(patch, status)
                    .query(Long.class)
                    .single();
        }

        private long item(String table, long versionId, String name) {
            return jdbc.sql("INSERT INTO " + table + " (catalog_version_id, name) VALUES (?, ?) RETURNING id")
                    .params(versionId, name)
                    .query(Long.class)
                    .single();
        }

        private void ascendancy(long versionId, long classId, String name) {
            jdbc.sql("INSERT INTO ascendancy (catalog_version_id, character_class_id, name) VALUES (?, ?, ?)")
                    .params(versionId, classId, name)
                    .update();
        }

        private HttpResponse<String> get() throws Exception {
            return send(port, "GET", PATH);
        }

        @Test
        @DisplayName("O1 — só a versão publicada aparece: o rascunho do próximo patch não vaza")
        void rascunhoNaoVaza() throws Exception {
            long publicado = version("0.4", "PUBLISHED");
            long ranger = item("character_class", publicado, "Ranger");
            ascendancy(publicado, ranger, "Deadeye");
            item("weapon_type", publicado, "Bow");
            item("playstyle", publicado, "À distância");
            long rascunho = version("0.5", "DRAFT");
            long druid = item("character_class", rascunho, "Druid");
            ascendancy(rascunho, druid, "Shaman");
            item("weapon_type", rascunho, "Talisman");

            HttpResponse<String> r = get();

            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).contains("\"patch\":\"0.4\"", "Deadeye", "À distância")
                    .doesNotContain("Druid", "Shaman", "Talisman", "0.5");
        }

        @Test
        @DisplayName("O2 — resposta cacheável em proxy (public) não carrega Set-Cookie de ninguém")
        void cachePublicoSemCookie() throws Exception {
            version("0.4", "PUBLISHED");

            HttpResponse<String> r = get();

            String cacheControl = r.headers().firstValue("Cache-Control").orElse("");
            assertThat(cacheControl).contains("max-age=300");
            if (!r.headers().allValues("Set-Cookie").isEmpty()) {
                assertThat(cacheControl)
                        .as("um proxy que guarde esta resposta entregaria o mesmo cookie (sessão/CSRF) a todos")
                        .doesNotContain("public")
                        .contains("private");
            }
        }

        @Test
        @DisplayName("O3 — sem Pragma/Expires contraditórios ao max-age")
        void semCabecalhosContraditorios() throws Exception {
            version("0.4", "PUBLISHED");

            HttpResponse<String> r = get();

            assertThat(r.headers().firstValue("Pragma")).isEmpty();
            assertThat(r.headers().firstValue("Expires")).isEmpty();
        }

        @Test
        @DisplayName("O4 — catálogo publicado vazio responde 200 com listas vazias, não 500")
        void catalogoVazio() throws Exception {
            version("0.4", "PUBLISHED");

            HttpResponse<String> r = get();

            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).contains("\"classes\":[]", "\"weaponTypes\":[]", "\"skillGems\":[]", "\"playstyles\":[]");
        }

        @Test
        @DisplayName("O5 — nome hostil no catálogo sai como JSON inerte (nosniff, application/json)")
        void nomeHostil() throws Exception {
            long v = version("0.4", "PUBLISHED");
            item("playstyle", v, "<img src=x onerror=alert(1)>");

            HttpResponse<String> r = get();

            assertThat(r.headers().firstValue("Content-Type")).hasValueSatisfying(ct -> assertThat(ct).startsWith("application/json"));
            assertThat(r.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        }

        @Test
        @DisplayName("O6 — sem catálogo: 503 em pt-BR que não fica em cache")
        void semCatalogo() throws Exception {
            HttpResponse<String> r = get();

            assertThat(r.statusCode()).isEqualTo(503);
            assertThat(r.body()).contains("catálogo");
            assertThat(r.headers().firstValue("Cache-Control").orElse("")).doesNotContain("max-age=300");
        }

        @Test
        @DisplayName("O7 — POST sem CSRF é recusado sem 500")
        void postRecusado() throws Exception {
            assertThat(send(port, "POST", PATH).statusCode()).isIn(403, 405);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.ai.google.genai.api-key=test-key-ficticia")
    @ActiveProfiles("test")
    @Import(TestcontainersConfiguration.class)
    @DisplayName("O8 — banco fora do ar")
    class BancoFora {

        @LocalServerPort private int port;
        @MockitoBean private CatalogRepositoryPort catalogs;

        @Test
        @DisplayName("falha inesperada vira ProblemDetail em pt-BR, sem detalhes internos e sem cache")
        void falhaInesperada() throws Exception {
            when(catalogs.loadPublished())
                    .thenThrow(new DataAccessResourceFailureException("Connection to db:5432 refused; senha=segredo"));

            HttpResponse<String> r = send(port, "GET", PATH);

            assertThat(r.statusCode()).isEqualTo(500);
            assertThat(r.body()).doesNotContain("db:5432", "segredo", "Exception", "at com.");
            assertThat(r.headers().firstValue("Cache-Control").orElse("")).doesNotContain("max-age=300");
            assertThat(r.body()).as("mesma língua do resto da API").contains("Algo deu errado");
        }
    }

    @Nested
    @DisplayName("O9 — concorrência no serviço (Collator compartilhado)")
    class Concorrencia {

        @Test
        @DisplayName("64 leituras simultâneas devolvem a mesma ordem")
        void mesmaOrdem() throws Exception {
            var service = new GetCatalogOptionsService(CatalogFixtures::catalog);
            var esperado = service.get();
            try (var pool = Executors.newFixedThreadPool(16)) {
                List<Callable<Object>> tarefas = IntStream.range(0, 64).<Callable<Object>>mapToObj(i -> service::get).toList();
                for (Future<Object> f : pool.invokeAll(tarefas)) {
                    assertThat(f.get()).isEqualTo(esperado);
                }
            }
        }
    }
}
