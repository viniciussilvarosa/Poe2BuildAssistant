package com.poe.poe2buildassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Perfil prod atrás de proxy HTTPS (Cloud Run): o cookie da sessão de login precisa ser Secure (#83). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@ActiveProfiles({"test", "prod"})
@Import(TestcontainersConfiguration.class)
class ProdSessionCookieTest {

    @LocalServerPort private int port;

    @Test
    @DisplayName("em prod, o JSESSIONID sai com Secure, HttpOnly e SameSite=Lax")
    void cookieDeSessaoSeguro() throws Exception {
        // O mesmo pedido que inicia o login com Google e cria a sessão; o proxy diz que a origem é HTTPS.
        HttpResponse<Void> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/authorization/google"))
                        .header("X-Forwarded-Proto", "https")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        List<String> cookies = r.headers().allValues("Set-Cookie");
        String sessao = cookies.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();

        assertThat(sessao).containsIgnoringCase("; Secure").containsIgnoringCase("; HttpOnly").containsIgnoringCase("SameSite=Lax");
    }
}
