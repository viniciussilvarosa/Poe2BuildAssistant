package com.poe.poe2buildassistant.application.service;

/**
 * Parâmetros da geração.
 *
 * @param maxAttempts chamadas ao LLM por pedido (1 + regenerações); cada uma gasta cota do provedor
 * @param sliceMaxItems limite por tipo de item no recorte do catálogo enviado ao LLM
 */
public record GenerationSettings(int maxAttempts, int sliceMaxItems) {

    public GenerationSettings {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts precisa ser >= 1");
        }
    }
}
