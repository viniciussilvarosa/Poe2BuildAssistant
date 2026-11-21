package com.poe.poe2buildassistant.adapter.in.web.build;

import java.time.Duration;

/**
 * Quanto o stream SSE da geração pode ficar aberto. Precisa passar o pior caso da geração (cada
 * tentativa esgotando o timeout do LLM), senão o stream fecha antes, sem evento {@code error}, e a
 * tela fica sem resposta (risco R-01 do #46).
 */
public record GenerationStreamTimeout(Duration value) {

    /** Folga para validar, consertar e gravar a build depois da última chamada ao LLM. */
    static final Duration MARGIN = Duration.ofSeconds(30);

    public static GenerationStreamTimeout covering(Duration llmTimeout, int maxAttempts) {
        return new GenerationStreamTimeout(llmTimeout.multipliedBy(maxAttempts).plus(MARGIN));
    }
}
