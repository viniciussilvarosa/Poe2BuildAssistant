package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.application.port.out.PassiveTreeSourcePort;
import com.poe.poe2buildassistant.application.service.ImportPassiveTreeService;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.DomainException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ataques ao import da árvore (card #6, PR #35), com o serviço real, o banco real e uma fonte
 * controlada pelo teste. Sem transação de teste: o que importa é a transação do próprio serviço.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    TestcontainersConfiguration.class,
    CatalogPersistenceAdapter.class,
    ImportPassiveTreeService.class,
    PassiveTreeImportChaosTest.Source.class
})
class PassiveTreeImportChaosTest {

    /** Fonte trocável por teste: cada ataque decide o que o "export" devolve e o que acontece durante o download. */
    @TestConfiguration
    static class Source {
        static Supplier<List<PassiveNotable>> behaviour = List::of;

        @Bean
        PassiveTreeSourcePort passiveTreeSource() {
            return () -> behaviour.get();
        }
    }

    @Autowired private ImportPassiveTreeService service;
    @Autowired private JdbcClient jdbc;
    private long draft;

    @BeforeEach
    void rascunho() {
        draft = jdbc.sql("INSERT INTO catalog_version (patch, status) VALUES ('1.0', 'DRAFT') RETURNING id").query(Long.class).single();
    }

    @AfterEach
    void limpar() {
        jdbc.sql("UPDATE catalog_version SET status = 'DRAFT' WHERE id = ?").param(draft).update();
        jdbc.sql("UPDATE catalog_version SET status = 'PUBLISHED' WHERE patch = '0.5'").update();
        jdbc.sql("DELETE FROM catalog_version WHERE id = ?").param(draft).update();
    }

    private List<String> notablesOf(long versionId) {
        return jdbc.sql("SELECT name FROM passive_notable WHERE catalog_version_id = ? ORDER BY name").param(versionId).query(String.class).list();
    }

    @Test
    @DisplayName("N1 — o download do export não segura uma transação (e uma conexão) do banco")
    void downloadForaDaTransacao() {
        AtomicBoolean transacaoAberta = new AtomicBoolean();
        Source.behaviour = () -> {
            transacaoAberta.set(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(new PassiveNotable("Her Final Bite", ""));
        };

        service.importInto(draft);

        assertThat(transacaoAberta).as("transação ativa durante o download de 5 MB do GitHub").isFalse();
    }

    @Test
    @DisplayName("N2 — rascunho publicado durante o download não recebe a escrita do import")
    void publicadoNoMeio() throws Exception {
        jdbc.sql("INSERT INTO passive_notable (catalog_version_id, name) VALUES (?, 'Original')").param(draft).update();
        Source.behaviour = () -> {
            // O admin publica o rascunho em outra aba enquanto o export ainda está descendo.
            Thread outraAba = new Thread(() -> {
                // Como a publicação real (#17): arquiva a vigente e publica o rascunho.
                jdbc.sql("UPDATE catalog_version SET status = 'ARCHIVED' WHERE patch = '0.5'").update();
                jdbc.sql("UPDATE catalog_version SET status = 'PUBLISHED' WHERE id = ?").param(draft).update();
            });
            outraAba.start();
            try {
                outraAba.join();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return List.of(new PassiveNotable("Her Final Bite", ""));
        };

        assertThatThrownBy(() -> service.importInto(draft)).isInstanceOf(CatalogVersionNotEditable.class);
        assertThat(notablesOf(draft)).containsExactly("Original");
    }

    @Test
    @DisplayName("N3 — nome acima do limite da coluna vira erro de domínio e não apaga os notables atuais")
    void nomeLongoDemais() {
        jdbc.sql("INSERT INTO passive_notable (catalog_version_id, name) VALUES (?, 'Original')").param(draft).update();
        Source.behaviour = () -> List.of(new PassiveNotable("Her Final Bite", ""), new PassiveNotable("X".repeat(130), ""));

        assertThatThrownBy(() -> service.importInto(draft)).isInstanceOf(DomainException.class);
        assertThat(notablesOf(draft)).containsExactly("Original");
    }
}
