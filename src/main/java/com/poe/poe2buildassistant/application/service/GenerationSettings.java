package com.poe.poe2buildassistant.application.service;

import java.time.Duration;

/**
 * Parâmetros da geração.
 *
 * @param maxAttempts chamadas ao LLM por pedido (1 + regenerações); cada uma gasta cota do provedor
 * @param sliceMaxItems limite por tipo de item no recorte do catálogo enviado ao LLM
 * @param timeBudget tempo de todas as tentativas juntas; depois disso nenhuma chamada nova começa
 */
public record GenerationSettings(int maxAttempts, int sliceMaxItems, Duration timeBudget) {

    /** Sem prazo próprio: só o timeout de cada chamada limita a geração. */
    public GenerationSettings(int maxAttempts, int sliceMaxItems) {
        this(maxAttempts, sliceMaxItems, Duration.ofDays(1));
    }

    public GenerationSettings {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts precisa ser >= 1");
        }
        if (timeBudget == null || timeBudget.isNegative() || timeBudget.isZero()) {
            throw new IllegalArgumentException("timeBudget precisa ser positivo");
        }
    }
}
