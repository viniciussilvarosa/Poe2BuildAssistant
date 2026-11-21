package com.poe.poe2buildassistant.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase.GoogleIdentity;
import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase.Provisioned;
import com.poe.poe2buildassistant.application.service.AccountSettings;
import com.poe.poe2buildassistant.application.service.ProvisionUserService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ataques ao provisionamento de contas (card #14, PR #36), com serviço e banco reais. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class, ProvisionUserService.class, ProvisioningChaosTest.Settings.class})
class ProvisioningChaosTest {

    @TestConfiguration
    static class Settings {
        @Bean
        AccountSettings accountSettings() {
            return new AccountSettings(Set.of());
        }
    }

    @Autowired private ProvisionUserService service;
    @Autowired private JdbcClient jdbc;

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM app_user").update();
    }

    @Test
    @DisplayName("L6 — dois callbacks do primeiro login ao mesmo tempo (duas abas) dão a mesma conta, sem erro")
    void primeiroLoginConcorrente() throws Exception {
        String sub = "g-" + UUID.randomUUID();
        CyclicBarrier juntos = new CyclicBarrier(2);
        Callable<Provisioned> login = () -> {
            juntos.await();
            return service.provision(new GoogleIdentity(sub, "ana@exemplo.test", true, "Ana"));
        };

        List<Provisioned> results;
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Provisioned>> futures = pool.invokeAll(List.of(login, login));
            results = List.of(futures.get(0).get(), futures.get(1).get());
        }

        assertThat(results.get(0).user().id()).isEqualTo(results.get(1).user().id());
        assertThat(jdbc.sql("SELECT count(*) FROM app_user WHERE google_sub = ?").param(sub).query(Integer.class).single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("L7 — nome do Google maior que a coluna não impede o login")
    void nomeLongo() {
        Provisioned p = service.provision(new GoogleIdentity("g-longo", "longo@exemplo.test", true, "N".repeat(300)));

        assertThat(p.user().displayName()).isNotBlank();
    }
}
