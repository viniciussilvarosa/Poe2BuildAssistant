package com.poe.poe2buildassistant.adapter.in.web.build;

import java.time.Duration;

/**
 * Quanto tempo uma geração pode levar antes de o usuário receber o erro: é o tempo que o stream SSE
 * fica aberto ({@code app.generation.max-duration}, 5 min, #88).
 *
 * <p>Precisa cobrir o pior caso (cada tentativa esgotando o timeout no modelo principal e no
 * reserva), senão o stream fecha antes, sem evento {@code error}, e a tela fica sem resposta (risco
 * R-01 do #46). Por isso a configuração é conferida na subida.
 */
public record GenerationStreamTimeout(Duration value) {

    /** Folga para validar, consertar e gravar a build depois da última chamada ao LLM. */
    static final Duration MARGIN = Duration.ofSeconds(30);

    /**
     * @param maxDuration limite configurado para a geração inteira
     * @param llmTimeout timeout de uma chamada ao LLM
     * @param maxAttempts tentativas por geração (1 + regenerações)
     * @param callsPerAttempt chamadas por tentativa: 2 com modelo reserva, 1 sem
     */
    public static GenerationStreamTimeout of(Duration maxDuration, Duration llmTimeout, int maxAttempts, int callsPerAttempt) {
        Duration worstCase = llmTimeout.multipliedBy((long) maxAttempts * callsPerAttempt).plus(MARGIN);
        if (maxDuration.compareTo(worstCase) < 0) {
            throw new IllegalStateException("app.generation.max-duration (" + maxDuration + ") menor que o pior caso da geração ("
                    + worstCase + " = app.ai.timeout x tentativas x modelos + " + MARGIN.toSeconds() + " s)");
        }
        return new GenerationStreamTimeout(maxDuration);
    }
}
