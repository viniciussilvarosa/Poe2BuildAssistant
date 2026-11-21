package com.poe.poe2buildassistant.adapter.in.web.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerationStreamTimeoutTest {

    @Test
    @DisplayName("cobre todas as tentativas esgotando o timeout do LLM, mais a folga")
    void cobreOPiorCaso() {
        var timeout = GenerationStreamTimeout.covering(Duration.ofSeconds(60), 2);

        assertThat(timeout.value()).isEqualTo(Duration.ofSeconds(150));
    }

    @Test
    @DisplayName("acompanha o timeout do LLM: subir um sobe o outro (R-01)")
    void acompanhaOTimeoutDoLlm() {
        var timeout = GenerationStreamTimeout.covering(Duration.ofSeconds(120), 3);

        assertThat(timeout.value()).isGreaterThan(Duration.ofSeconds(120).multipliedBy(3));
    }
}
