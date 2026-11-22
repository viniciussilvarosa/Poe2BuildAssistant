package com.poe.poe2buildassistant.adapter.in.web.build;

import java.time.Duration;

/**
 * Quanto tempo uma geração pode levar antes de o usuário receber o erro: é o tempo que o stream SSE
 * fica aberto ({@code app.generation.max-duration}, 5 min, #88).
 *
 * <p>As tentativas usam o limite menos {@link #MARGIN} como prazo (cada chamada ao LLM recebe só o
 * que resta dele), e a folga cobre validar, gravar e responder. Assim o stream nunca fecha antes da
 * geração terminar sem evento {@code error} (risco R-01 do #46).
 */
public record GenerationStreamTimeout(Duration value) {

    /** Folga para validar, consertar e gravar a build depois da última chamada ao LLM. */
    public static final Duration MARGIN = Duration.ofSeconds(30);

    public static GenerationStreamTimeout of(Duration maxDuration) {
        if (maxDuration.compareTo(MARGIN.multipliedBy(2)) < 0) {
            throw new IllegalStateException("app.generation.max-duration (" + maxDuration + ") precisa ser de pelo menos "
                    + MARGIN.multipliedBy(2).toSeconds() + " s: " + MARGIN.toSeconds() + " s ficam de folga para gravar a build");
        }
        return new GenerationStreamTimeout(maxDuration);
    }

    /** Prazo das tentativas: o limite da geração menos a folga para gravar e responder. */
    public Duration attemptsBudget() {
        return value.minus(MARGIN);
    }
}
