package com.poe.poe2buildassistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O cliente do Gemini não pode repetir chamadas por conta própria (#64): quem decide é o caso de uso. */
class GeminiClientTest {

    private static final String QUOTA_EXHAUSTED = """
            {"error":{"code":429,"message":"You exceeded your current quota. Please retry in 38s.",
             "status":"RESOURCE_EXHAUSTED"}}""";

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            byte[] body = QUOTA_EXHAUSTED.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    @DisplayName("um 429 chega ao app na primeira resposta, sem retry com backoff do SDK")
    void semRetryDoSdk() {
        Client client = AiConfig.geminiClient("chave-ficticia", Optional.of("http://127.0.0.1:" + server.getAddress().getPort()));
        long start = System.nanoTime();

        assertThatThrownBy(() -> client.models.generateContent("gemini-3.6-flash", "oi", null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(429));

        assertThat(hits).hasValue(1);
        // Folga para runner lento (no CI, 2,02 s): o retry do SDK levaria no mínimo 1 + 2 + 4 + 8 = 15 s.
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }
}
