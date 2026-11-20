package com.poe.poe2buildassistant.domain.build;

/** Etapas da geração exibidas ao usuário na tela de progresso, nesta ordem. */
public enum GenerationStep {
    VALIDATING_INPUT,
    GENERATING,
    VALIDATING_OUTPUT,
    SAVING,
    DONE
}
