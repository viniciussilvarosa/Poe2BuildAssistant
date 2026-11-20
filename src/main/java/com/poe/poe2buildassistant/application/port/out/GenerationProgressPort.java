package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.build.GenerationStep;

/** Recebe o progresso de UMA geração (por requisição, nunca compartilhado entre usuários). */
@FunctionalInterface
public interface GenerationProgressPort {

    void emit(GenerationStep step);

    static GenerationProgressPort none() {
        return step -> {};
    }
}
