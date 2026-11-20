package com.poe.poe2buildassistant.adapter.out.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ataques ao contador de cota (card #9): defesa em profundidade e concorrência. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PostgresQuotaCounterAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresQuotaCounterChaosTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 21);

    @Autowired private PostgresQuotaCounterAdapter adapter;
    @Autowired private JdbcClient jdbc;

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM quota_counter").update();
    }

    @ParameterizedTest(name = "Q7: chave anônima \"{0}\" é recusada pelo banco")
    @ValueSource(strings = {"anon:203.0.113.7", "anon:2001:db8::1", "anon:abc", "anon:GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG"})
    void bancoRecusaChaveAnonimaQueNaoEhHash(String key) {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO quota_counter (requester_key, day, count) VALUES (?, ?, 1)")
                        .params(key, DIA)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Q7: a chave anônima real (hash) continua aceita")
    void chaveHashAceita() {
        assertThat(adapter.tryConsume(Requester.anonymousFromIp("203.0.113.7", "salt"), DIA, 3)).isTrue();
    }

    @Test
    @DisplayName("Q8: 20 releases simultâneos nunca deixam o contador negativo nem lançam erro")
    void releasesConcorrentes() throws Exception {
        Requester r = new Requester.Authenticated("42");
        for (int i = 0; i < 5; i++) {
            adapter.tryConsume(r, DIA, 5);
        }
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> adapter.release(r, DIA)));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(adapter.current(r, DIA)).isZero();
    }
}
