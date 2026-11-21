package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.application.service.ManageCatalogService;
import com.poe.poe2buildassistant.application.service.PublishCatalogVersionService;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.DomainException;
import com.poe.poe2buildassistant.domain.shared.DraftAlreadyExists;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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

/** Ataques à curadoria do catálogo (card #17, PR #37), com serviços e banco reais, sem transação de teste. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TestcontainersConfiguration.class, CatalogPersistenceAdapter.class, ManageCatalogService.class, PublishCatalogVersionService.class})
class CatalogAdminChaosTest {

    private static final String ADMIN = "admin-de-teste";

    @Autowired private ManageCatalogService manage;
    @Autowired private PublishCatalogVersionService publish;
    @Autowired private CatalogRepositoryPort catalogs;
    @Autowired private JdbcClient jdbc;

    /** Volta ao estado do seed: só a 0.5, publicada. */
    @AfterEach
    void restaurar() {
        jdbc.sql("UPDATE catalog_version SET status = 'ARCHIVED' WHERE status = 'PUBLISHED' AND patch <> '0.5'").update();
        jdbc.sql("UPDATE catalog_version SET status = 'PUBLISHED' WHERE patch = '0.5'").update();
        jdbc.sql("DELETE FROM catalog_version WHERE patch <> '0.5'").update();
    }

    @Test
    @DisplayName("C1 — dois admins criando rascunho ao mesmo tempo: um ganha, o outro recebe DraftAlreadyExists (409), não 500")
    void rascunhoConcorrente() throws Exception {
        CyclicBarrier juntos = new CyclicBarrier(2);
        Callable<Object> cria = () -> {
            juntos.await();
            return manage.createDraft("0.6", ADMIN);
        };

        int ok = 0;
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (Future<Object> f : pool.invokeAll(List.of(cria, cria))) {
                try {
                    f.get();
                    ok++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(DraftAlreadyExists.class);
                }
            }
        }
        assertThat(ok).isEqualTo(1);
    }

    @Test
    @DisplayName("C2 — apóstrofo tipográfico no PUT não cria uma segunda gema que quebra a carga do catálogo")
    void apostrofoDuplicado() {
        long draft = manage.createDraft("0.6", ADMIN).id();

        manage.saveItem(draft, CatalogItem.skillGem("Alchemist’s Boon", List.of()), ADMIN);

        assertThatCode(() -> manage.draftCatalog(draft)).doesNotThrowAnyException();
        Catalog catalog = manage.draftCatalog(draft);
        assertThat(catalog.skillGems().stream().filter(g -> g.name().startsWith("Alchemist"))).hasSize(1);
    }

    @Test
    @DisplayName("C3 — rascunho sem gemas, classes ou notables suficientes não pode ser publicado")
    void publicaRascunhoInutil() {
        long draft = manage.createDraft("0.6", ADMIN).id();
        jdbc.sql("DELETE FROM skill_gem WHERE catalog_version_id = ?").param(draft).update();

        assertThatThrownBy(() -> publish.publish(draft, ADMIN)).isInstanceOf(DomainException.class);
        assertThat(catalogs.loadPublished().version().patch()).isEqualTo("0.5");
    }

    @Test
    @DisplayName("C4 — versão arquivada não aceita edição")
    void arquivadaImutavel() {
        long draft = manage.createDraft("0.6", ADMIN).id();
        publish.publish(draft, ADMIN);
        long arquivada = jdbc.sql("SELECT id FROM catalog_version WHERE patch = '0.5'").query(Long.class).single();

        assertThatThrownBy(() -> manage.saveItem(arquivada, CatalogItem.playstyle("Novo"), ADMIN))
                .isInstanceOf(CatalogVersionNotEditable.class);
    }

    @Test
    @DisplayName("C5 — patch maior que a coluna vira erro de domínio (400), não 500")
    void patchLongo() {
        assertThatThrownBy(() -> manage.createDraft("0.6-" + "x".repeat(60), ADMIN)).isInstanceOf(DomainException.class);
    }
}
