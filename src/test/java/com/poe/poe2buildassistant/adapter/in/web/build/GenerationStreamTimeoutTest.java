package com.poe.poe2buildassistant.adapter.in.web.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerationStreamTimeoutTest {

    @Test
    @DisplayName("a geração tem o limite configurado (5 min), que cobre o pior caso com o modelo reserva (#88)")
    void limiteConfigurado() {
        var timeout = GenerationStreamTimeout.of(Duration.ofMinutes(5), Duration.ofSeconds(60), 2, 2);

        assertThat(timeout.value()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("configuração de produção: 45 s x 2 tentativas x 3 modelos + 30 s cabe exatamente em 5 min")
    void producaoComTresModelos() {
        assertThat(GenerationStreamTimeout.of(Duration.ofMinutes(5), Duration.ofSeconds(45), 2, 3).value())
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("limite menor que o pior caso (timeout x tentativas x modelos + folga) recusa subir (R-01)")
    void limiteCurtoRecusado() {
        // 120 s x 2 tentativas x 2 modelos + 30 s = 510 s > 300 s: o stream fecharia antes da geração.
        assertThatThrownBy(() -> GenerationStreamTimeout.of(Duration.ofMinutes(5), Duration.ofSeconds(120), 2, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.generation.max-duration");
    }

    @Test
    @DisplayName("sem modelo reserva, o pior caso conta uma chamada por tentativa")
    void semReserva() {
        var timeout = GenerationStreamTimeout.of(Duration.ofSeconds(150), Duration.ofSeconds(60), 2, 1);

        assertThat(timeout.value()).isEqualTo(Duration.ofSeconds(150));
    }
}
