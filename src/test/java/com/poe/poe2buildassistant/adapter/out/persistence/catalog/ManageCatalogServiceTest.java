package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.application.service.GetCatalogOptionsService;
import com.poe.poe2buildassistant.application.service.ManageCatalogService;
import com.poe.poe2buildassistant.application.service.PublishCatalogVersionService;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.DraftAlreadyExists;
import com.poe.poe2buildassistant.domain.shared.InvalidCatalogChange;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Administração do catálogo com o banco real e o catálogo 0.5 do seed: rascunho, edição e
 * publicação (card #17). Cada teste roda numa transação desfeita no fim.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    CatalogPersistenceAdapter.class,
    ManageCatalogService.class,
    PublishCatalogVersionService.class,
    GetCatalogOptionsService.class
})
class ManageCatalogServiceTest {

    private static final String ADMIN = "admin-de-teste";

    @Autowired private ManageCatalogService manage;
    @Autowired private PublishCatalogVersionService publish;
    @Autowired private GetCatalogOptionsService options;
    @Autowired private CatalogRepositoryPort catalogs;
    @Autowired private JdbcClient jdbc;

    private int count(String table, long versionId) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE catalog_version_id = ?").param(versionId).query(Integer.class).single();
    }

    @Test
    @DisplayName("criar DRAFT copia todos os itens da versão publicada")
    void criaRascunho() {
        long published = catalogs.loadPublished().version().id();

        CatalogVersion draft = manage.createDraft("0.6", ADMIN);

        assertThat(draft.status()).isEqualTo(CatalogVersion.Status.DRAFT);
        assertThat(draft.patch()).isEqualTo("0.6");
        for (String table : List.of("character_class", "ascendancy", "weapon_type", "skill_gem", "skill_gem_weapon_type",
                "support_gem", "passive_notable", "playstyle")) {
            assertThat(count(table, draft.id())).as(table).isEqualTo(count(table, published)).isPositive();
        }
    }

    @Test
    @DisplayName("só pode existir um DRAFT por vez")
    void umRascunho() {
        manage.createDraft("0.6", ADMIN);

        assertThatThrownBy(() -> manage.createDraft("0.7", ADMIN)).isInstanceOf(DraftAlreadyExists.class);
    }

    @Test
    @DisplayName("adicionar gema ao DRAFT não altera a versão publicada")
    void editaSoORascunho() {
        long draft = manage.createDraft("0.6", ADMIN).id();

        manage.saveItem(draft, CatalogItem.skillGem("Nova Gem", List.of("Bow")), ADMIN);

        assertThat(manage.draftCatalog(draft).findSkillGem("Nova Gem")).hasValueSatisfying(g -> assertThat(g.weaponTypes()).containsExactly("Bow"));
        assertThat(catalogs.loadPublished().findSkillGem("Nova Gem")).isEmpty();
    }

    @Test
    @DisplayName("editar versão PUBLISHED é recusado")
    void publicadaImutavel() {
        long published = catalogs.loadPublished().version().id();

        assertThatThrownBy(() -> manage.saveItem(published, CatalogItem.playstyle("Novo"), ADMIN))
                .isInstanceOf(CatalogVersionNotEditable.class);
        assertThatThrownBy(() -> manage.removeItem(published, CatalogItem.Type.PLAYSTYLE, "Tanque", ADMIN))
                .isInstanceOf(CatalogVersionNotEditable.class);
    }

    @Test
    @DisplayName("ascendência sem classe existente no DRAFT é recusada")
    void ascendenciaSemClasse() {
        long draft = manage.createDraft("0.6", ADMIN).id();

        assertThatThrownBy(() -> manage.saveItem(draft, CatalogItem.ascendancy("Nova Ascendência", "Classe Inexistente"), ADMIN))
                .isInstanceOf(InvalidCatalogChange.class)
                .hasMessageContaining("Classe Inexistente");
    }

    @Test
    @DisplayName("remover item do DRAFT tira só ele, e o publicado continua com o item")
    void removeDoRascunho() {
        long draft = manage.createDraft("0.6", ADMIN).id();

        manage.removeItem(draft, CatalogItem.Type.SUPPORT_GEM, "Pierce I", ADMIN);

        assertThat(manage.draftCatalog(draft).findSupportGem("Pierce I")).isEmpty();
        assertThat(catalogs.loadPublished().findSupportGem("Pierce I")).isPresent();
    }

    @Test
    @DisplayName("publicar torna o DRAFT a versão vigente e arquiva a anterior")
    void publica() {
        long anterior = catalogs.loadPublished().version().id();
        long draft = manage.createDraft("0.6", ADMIN).id();

        CatalogVersion published = publish.publish(draft, ADMIN);

        assertThat(published.status()).isEqualTo(CatalogVersion.Status.PUBLISHED);
        assertThat(catalogs.loadPublished().version().patch()).isEqualTo("0.6");
        assertThat(jdbc.sql("SELECT status FROM catalog_version WHERE id = ?").param(anterior).query(String.class).single())
                .isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("após publicar, opções do formulário refletem a nova versão")
    void opcoesRefletem() {
        long draft = manage.createDraft("0.6", ADMIN).id();
        manage.saveItem(draft, CatalogItem.playstyle("Veneno"), ADMIN);

        publish.publish(draft, ADMIN);

        assertThat(options.get().patch()).isEqualTo("0.6");
        assertThat(options.get().playstyles()).contains("Veneno");
    }

    @Test
    @DisplayName("publicar versão que não é rascunho é recusado")
    void publicaSoRascunho() {
        long published = catalogs.loadPublished().version().id();

        assertThatThrownBy(() -> publish.publish(published, ADMIN)).isInstanceOf(CatalogVersionNotEditable.class);
    }

    @Test
    @DisplayName("o catálogo do rascunho lido de volta passa nas invariantes do domínio")
    void rascunhoCoerente() {
        long draft = manage.createDraft("0.6", ADMIN).id();

        Catalog copy = manage.draftCatalog(draft);

        assertThat(copy.version().status()).isEqualTo(CatalogVersion.Status.DRAFT);
        assertThat(copy.findAscendancy("Deadeye")).hasValueSatisfying(a -> assertThat(a.characterClass().name()).isEqualTo("Ranger"));
    }
}
