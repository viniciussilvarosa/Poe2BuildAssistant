package com.poe.poe2buildassistant.domain.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuotaPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");
    private final QuotaPolicy policy = new QuotaPolicy(3, 10);
    private final Requester visitante = Requester.anonymousFromIp("203.0.113.7", "salt");
    private final Requester logado = new Requester.Authenticated("42");

    @Test
    @DisplayName("visitante dentro do limite é permitido")
    void visitanteDentroDoLimite() {
        assertThat(policy.decide(visitante, 2, NOW).allowed()).isTrue();
    }

    @Test
    @DisplayName("visitante no limite é bloqueado com retryAt na próxima meia-noite UTC")
    void visitanteNoLimite() {
        QuotaDecision decision = policy.decide(visitante, 3, NOW);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAt()).contains(Instant.parse("2026-11-22T00:00:00Z"));
    }

    @Test
    @DisplayName("usuário logado usa o limite maior")
    void logadoUsaLimiteMaior() {
        assertThat(policy.decide(logado, 5, NOW).allowed()).isTrue();
        assertThat(policy.limitFor(logado)).isEqualTo(10);
        assertThat(policy.limitFor(visitante)).isEqualTo(3);
    }

    @Test
    @DisplayName("exatamente à meia-noite o reset é a meia-noite seguinte")
    void resetNaMeiaNoite() {
        assertThat(policy.nextReset(Instant.parse("2026-11-22T00:00:00Z"))).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
    }

    @Test
    @DisplayName("o dia de contagem é o dia UTC, não o local")
    void diaUtc() {
        assertThat(policy.dayOf(Instant.parse("2026-11-21T23:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(policy.dayOf(Instant.parse("2026-11-22T00:00:01Z"))).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    @DisplayName("limites negativos são recusados")
    void limitesNegativos() {
        assertThatThrownBy(() -> new QuotaPolicy(-1, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
