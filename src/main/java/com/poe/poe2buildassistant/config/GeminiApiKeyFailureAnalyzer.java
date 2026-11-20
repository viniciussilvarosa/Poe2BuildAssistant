package com.poe.poe2buildassistant.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Traduz a falha de startup do Spring AI sem chave do Gemini para uma mensagem que nomeia a
 * variável de ambiente que o desenvolvedor precisa definir (chaos K1).
 */
class GeminiApiKeyFailureAnalyzer extends AbstractFailureAnalyzer<IllegalStateException> {

    private static final String SPRING_AI_MESSAGE = "Incomplete Google GenAI configuration";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, IllegalStateException cause) {
        String message = cause.getMessage();
        if (message == null || !message.contains(SPRING_AI_MESSAGE)) {
            return null;
        }
        return new FailureAnalysis(
                "A chave da API do Google Gemini não foi configurada.",
                "Defina a variável de ambiente GEMINI_API_KEY (veja .env.example).",
                cause);
    }
}
