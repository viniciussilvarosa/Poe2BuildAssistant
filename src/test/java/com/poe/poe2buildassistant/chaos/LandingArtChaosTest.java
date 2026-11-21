package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Ataques à arte de fundo da landing (card #2, redesign): servidor real e CSS final do build. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LandingArtChaosTest {

    @LocalServerPort private int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<byte[]> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private String css() throws Exception {
        String index = new String(get("/").body());
        Matcher m = Pattern.compile("href=\"(/assets/[^\"]+\\.css)\"").matcher(index);
        assertThat(m.find()).isTrue();
        return new String(get(m.group(1)).body());
    }

    private List<String> artUrls(String css) {
        List<String> urls = new ArrayList<>();
        Matcher m = Pattern.compile("(/assets/key-art-[^\"')\\s]+)").matcher(css);
        while (m.find()) {
            if (!urls.contains(m.group(1))) {
                urls.add(m.group(1));
            }
        }
        return urls;
    }

    @Test
    @DisplayName("L1: toda arte referenciada no CSS é servida como imagem, com cache imutável")
    void arteServida() throws Exception {
        List<String> urls = artUrls(css());
        assertThat(urls).hasSize(4);
        for (String url : urls) {
            HttpResponse<byte[]> r = get(url);
            assertThat(r.statusCode()).as(url).isEqualTo(200);
            assertThat(r.headers().firstValue("Content-Type").orElse("")).as(url).startsWith("image/");
            assertThat(r.headers().firstValue("Cache-Control").orElse("")).as(url).contains("immutable");
        }
    }

    @Test
    @DisplayName("L2: navegadores sem image-set(type()) recebem um url(jpg) simples de reserva")
    void reservaSemImageSet() throws Exception {
        assertThat(css()).containsPattern("background-image:url\\([^)]*key-art-1600[^)]*\\.jpg\\)");
    }

    @Test
    @DisplayName("L3: a arte cabe no orçamento de peso (AVIF desktop ≤150 KB, celular ≤80 KB; JPEG ≤300/≤150 KB)")
    void pesoDaArte() throws Exception {
        for (String url : artUrls(css())) {
            int kb = get(url).body().length / 1024;
            int limite = url.contains("1600") ? (url.endsWith(".avif") ? 150 : 300) : (url.endsWith(".avif") ? 80 : 150);
            assertThat(kb).as("%s = %d KB", url, kb).isLessThanOrEqualTo(limite);
        }
    }
}
