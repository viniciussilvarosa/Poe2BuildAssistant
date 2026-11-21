package com.poe.poe2buildassistant.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.poe.poe2buildassistant.adapter.out.ai.AiProperties;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {

    /**
     * Substitui o cliente do auto-config do Spring AI só para desligar o retry interno do SDK:
     * por padrão ele repete 408/429/5xx até 5 vezes com backoff (1, 2, 4, 8 s), o que consumia os
     * 25 s do {@code app.ai.timeout} e escondia o retryAt do 429 (#64). Quem decide repetir é o
     * caso de uso.
     */
    @Bean
    Client googleGenAiClient(Environment environment) {
        // Environment, não @Value: o bean é criado cedo, antes de os placeholders serem resolvidos.
        String apiKey = environment.getProperty("spring.ai.google.genai.api-key", "");
        if (apiKey.isBlank()) {
            // Mesma mensagem do auto-config do Spring AI: o GeminiApiKeyFailureAnalyzer nomeia a variável.
            throw new IllegalStateException(GeminiApiKeyFailureAnalyzer.SPRING_AI_MESSAGE + ": api-key vazia");
        }
        return geminiClient(apiKey, Optional.empty());
    }

    static Client geminiClient(String apiKey, Optional<String> baseUrl) {
        HttpOptions.Builder http = HttpOptions.builder().retryOptions(HttpRetryOptions.builder().attempts(1).build());
        baseUrl.ifPresent(http::baseUrl);
        return Client.builder().apiKey(apiKey).httpOptions(http.build()).build();
    }
}
