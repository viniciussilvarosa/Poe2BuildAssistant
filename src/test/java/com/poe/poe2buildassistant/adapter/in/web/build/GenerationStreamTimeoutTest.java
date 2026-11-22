package com.poe.poe2buildassistant.adapter.in.web.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerationStreamTimeoutTest {

    @Test
    @DisplayName("a geração tem o limite configurado (5 min) e as tentativas usam 5 min - 30 s de prazo")
    void limiteConfigurado() {
        var timeout = GenerationStreamTimeout.of(Duration.ofMinutes(5));

        assertThat(timeout.value()).isEqualTo(Duration.ofMinutes(5));
        assertThat(timeout.attemptsBudget()).isEqualTo(Duration.ofSeconds(270));
    }

    @Test
    @DisplayName("limite que não deixa a folga de gravar recusa subir (R-01)")
    void limiteCurtoRecusado() {
        assertThatThrownBy(() -> GenerationStreamTimeout.of(Duration.ofSeconds(59)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.generation.max-duration");
    }

    @Test
    @DisplayName("o menor limite aceito é o dobro da folga")
    void menorLimite() {
        assertThat(GenerationStreamTimeout.of(Duration.ofSeconds(60)).attemptsBudget()).isEqualTo(Duration.ofSeconds(30));
    }
}
