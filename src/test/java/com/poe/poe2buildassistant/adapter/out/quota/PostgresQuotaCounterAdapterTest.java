package com.poe.poe2buildassistant.adapter.out.quota;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sem transação de teste: a concorrência precisa de commits reais entre threads. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PostgresQuotaCounterAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresQuotaCounterAdapterTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 21);
    private static final String IP = "203.0.113.7";

    @Autowired private PostgresQuotaCounterAdapter adapter;
    @Autowired private JdbcClient jdbc;

    private final Requester visitante = Requester.anonymousFromIp(IP, "salt");
    private final Requester logado = new Requester.Authenticated("42");

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM quota_counter").update();
    }

    private List<Boolean> emParalelo(int threads, Callable<Boolean> task) throws Exception {
        List<Future<Boolean>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(task));
            }
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }

    @Test
    @DisplayName("consome até o limite e então recusa")
    void consomeAteOLimite() {
        assertThat(List.of(
                        adapter.tryConsume(visitante, DIA, 3),
                        adapter.tryConsume(visitante, DIA, 3),
                        adapter.tryConsume(visitante, DIA, 3),
                        adapter.tryConsume(visitante, DIA, 3)))
                .containsExactly(true, true, true, false);
        assertThat(adapter.current(visitante, DIA)).isEqualTo(3);
    }

    @Test
    @DisplayName("incremento é atômico sob concorrência")
    void atomicoSobConcorrencia() throws Exception {
        emParalelo(10, () -> adapter.tryConsume(visitante, DIA, 100));

        assertThat(adapter.current(visitante, DIA)).isEqualTo(10);
    }

    @Test
    @DisplayName("sob concorrência, nunca passa do limite: 20 pedidos, limite 5 → exatamente 5 aceitos")
    void nuncaPassaDoLimite() throws Exception {
        List<Boolean> results = emParalelo(20, () -> adapter.tryConsume(visitante, DIA, 5));

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(5);
        assertThat(adapter.current(visitante, DIA)).isEqualTo(5);
    }

    @Test
    @DisplayName("contadores de dias diferentes são independentes")
    void diasIndependentes() {
        for (int i = 0; i < 3; i++) {
            adapter.tryConsume(visitante, DIA, 10);
        }

        assertThat(adapter.current(visitante, DIA)).isEqualTo(3);
        assertThat(adapter.current(visitante, DIA.plusDays(1))).isZero();
        assertThat(adapter.tryConsume(visitante, DIA.plusDays(1), 3)).isTrue();
    }

    @Test
    @DisplayName("visitante e usuário logado têm contadores separados")
    void requestersIndependentes() {
        adapter.tryConsume(visitante, DIA, 1);

        assertThat(adapter.tryConsume(visitante, DIA, 1)).isFalse();
        assertThat(adapter.tryConsume(logado, DIA, 1)).isTrue();
    }

    @Test
    @DisplayName("release devolve uma unidade e nunca fica negativo")
    void releaseDevolve() {
        adapter.tryConsume(visitante, DIA, 1);
        adapter.release(visitante, DIA);
        adapter.release(visitante, DIA);

        assertThat(adapter.current(visitante, DIA)).isZero();
        assertThat(adapter.tryConsume(visitante, DIA, 1)).isTrue();
    }

    @Test
    @DisplayName("limite zero recusa sem gravar nada")
    void limiteZero() {
        assertThat(adapter.tryConsume(visitante, DIA, 0)).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM quota_counter").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("IP é armazenado apenas como hash")
    void ipSoComoHash() {
        adapter.tryConsume(visitante, DIA, 3);

        List<String> keys = jdbc.sql("SELECT requester_key FROM quota_counter").query(String.class).list();
        assertThat(keys).hasSize(1).allSatisfy(k -> assertThat(k).startsWith("anon:").doesNotContain(IP));
    }
}
