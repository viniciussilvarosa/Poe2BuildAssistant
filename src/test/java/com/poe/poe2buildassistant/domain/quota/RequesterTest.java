package com.poe.poe2buildassistant.domain.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequesterTest {

    @Test
    @DisplayName("mesmo IP e salt geram a mesma chave; a chave não contém o IP")
    void chaveAnonimaDeterministica() {
        Requester a = Requester.anonymousFromIp("203.0.113.7", "salt");
        Requester b = Requester.anonymousFromIp("203.0.113.7", "salt");

        assertThat(a.key()).isEqualTo(b.key()).startsWith("anon:").doesNotContain("203.0.113.7");
    }

    @Test
    @DisplayName("salt diferente gera chave diferente")
    void saltMudaChave() {
        assertThat(Requester.anonymousFromIp("203.0.113.7", "a").key())
                .isNotEqualTo(Requester.anonymousFromIp("203.0.113.7", "b").key());
    }

    @Test
    @DisplayName("usuário logado tem chave por id e tipo próprio")
    void chaveLogado() {
        Requester r = new Requester.Authenticated("42");
        assertThat(r.key()).isEqualTo("user:42");
        assertThat(r.type()).isEqualTo("authenticated");
    }

    @Test
    @DisplayName("IP em branco é recusado")
    void ipEmBranco() {
        assertThatThrownBy(() -> Requester.anonymousFromIp(" ", "salt")).isInstanceOf(IllegalArgumentException.class);
    }
}
