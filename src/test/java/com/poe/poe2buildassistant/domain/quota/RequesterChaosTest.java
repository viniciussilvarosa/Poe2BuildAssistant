package com.poe.poe2buildassistant.domain.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Ataques à identidade do visitante (card #9): contornar a cota e expor o IP. */
class RequesterChaosTest {

    private static final String SALT = "salt-de-teste";

    private static String key(String ip) {
        return Requester.anonymousFromIp(ip, SALT).key();
    }

    @Nested
    @DisplayName("Q1–Q3 — o mesmo cliente não ganha cota nova mudando a forma do IP")
    class MesmoCliente {

        @ParameterizedTest(name = "\"{0}\" e \"{1}\" são o mesmo cliente")
        @CsvSource({
            "2001:db8::1, 2001:0DB8:0:0:0:0:0:1",
            "2001:db8::1, 2001:DB8::1",
            "::ffff:203.0.113.7, 203.0.113.7",
            "2001:db8:1:2::1, 2001:db8:1:2:ffff:ffff:ffff:abcd",
            "' 203.0.113.7 ', 203.0.113.7"
        })
        void mesmaChave(String a, String b) {
            assertThat(key(a)).isEqualTo(key(b));
        }
    }

    @Nested
    @DisplayName("Q4 — clientes diferentes continuam separados")
    class ClientesDiferentes {

        @ParameterizedTest(name = "\"{0}\" e \"{1}\" são clientes diferentes")
        @CsvSource({
            "2001:db8:1:2::1, 2001:db8:1:3::1",
            "203.0.113.7, 203.0.113.8",
            "203.0.113.7, 2001:db8::1"
        })
        void chavesDiferentes(String a, String b) {
            assertThat(key(a)).isNotEqualTo(key(b));
        }
    }

    @Nested
    @DisplayName("Q5/Q6 — entradas que enfraquecem o hash ou não são IP")
    class EntradasHostis {

        @ParameterizedTest(name = "salt \"{0}\" é recusado")
        @ValueSource(strings = {"", "   "})
        void saltVazio(String salt) {
            assertThatThrownBy(() -> Requester.anonymousFromIp("203.0.113.7", salt))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("salt nulo é recusado")
        void saltNulo() {
            assertThatThrownBy(() -> Requester.anonymousFromIp("203.0.113.7", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "\"{0}\" não é IP e é recusado")
        @ValueSource(strings = {"não-é-ip", "203.0.113.7, 10.0.0.1", "999.1.1.1", "localhost", "example.com"})
        void naoEhIp(String ip) {
            assertThatThrownBy(() -> Requester.anonymousFromIp(ip, SALT)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
