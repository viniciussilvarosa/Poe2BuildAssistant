package com.poe.poe2buildassistant.adapter.out.persistence.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, BuildPersistenceAdapter.class})
class BuildPersistenceAdapterTest {

    @Autowired private BuildPersistenceAdapter adapter;
    @Autowired private JdbcClient jdbc;

    private long versionId;

    @BeforeEach
    void versao() {
        // A carga inicial (V5) já publica o patch 0.5; o teste monta o próprio catálogo do zero.
        jdbc.sql("DELETE FROM catalog_version").update();
        versionId = jdbc.sql("INSERT INTO catalog_version (patch, status) VALUES ('0.4', 'PUBLISHED') RETURNING id")
                .query(Long.class)
                .single();
    }

    private Build build(Optional<String> owner, Instant createdAt) {
        var request = new BuildRequest(
                "arqueiro mágico", Constraints.none().withCharacterClass("Ranger").withWeaponType("Bow"));
        return new Build(UUID.randomUUID(), owner, versionId, request, CatalogFixtures.validDraft(), createdAt);
    }

    @Test
    @DisplayName("salva e recarrega conteúdo, pedido, dono e versão do catálogo")
    void salvaERecarrega() {
        Build original = build(Optional.of("42"), Instant.parse("2026-11-21T15:00:00Z"));

        adapter.save(original);
        Build lido = adapter.findById(original.id()).orElseThrow();

        assertThat(lido).isEqualTo(original);
        assertThat(lido.content().passiveTree().notables()).isEqualTo(CatalogFixtures.FIVE_NOTABLES);
        assertThat(lido.request().constraints().characterClass()).contains("Ranger");
        assertThat(lido.request().constraints().ascendancy()).isEmpty();
        assertThat(lido.catalogVersionId()).isEqualTo(versionId);
    }

    @Test
    @DisplayName("build de visitante é salva sem dono e não aparece em histórico de ninguém")
    void visitanteSemDono() {
        Build anonimo = build(Optional.empty(), Instant.parse("2026-11-21T15:00:00Z"));

        adapter.save(anonimo);

        assertThat(adapter.findById(anonimo.id())).map(Build::ownerId).contains(Optional.empty());
        assertThat(adapter.findByOwner("42")).isEmpty();
    }

    @Test
    @DisplayName("histórico do dono vem do mais recente para o mais antigo e só com as builds dele")
    void historicoDoDono() {
        Build antiga = adapter.save(build(Optional.of("42"), Instant.parse("2026-11-20T10:00:00Z")));
        Build nova = adapter.save(build(Optional.of("42"), Instant.parse("2026-11-21T10:00:00Z")));
        adapter.save(build(Optional.of("7"), Instant.parse("2026-11-21T11:00:00Z")));
        adapter.save(build(Optional.empty(), Instant.parse("2026-11-21T12:00:00Z")));

        assertThat(adapter.findByOwner("42")).extracting(Build::id).containsExactly(nova.id(), antiga.id());
    }

    @Test
    @DisplayName("id inexistente devolve vazio")
    void idInexistente() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("build precisa apontar para uma versão de catálogo existente")
    void versaoInexistente() {
        var request = new BuildRequest("x", Constraints.none());
        Build orfa = new Build(UUID.randomUUID(), Optional.empty(), 999_999L, request, CatalogFixtures.validDraft(), Instant.now());

        assertThatThrownBy(() -> {
                    adapter.save(orfa);
                    jdbc.sql("SELECT 1").query(Integer.class).single();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("conteúdo é gravado como jsonb consultável")
    void conteudoJsonb() {
        Build b = adapter.save(build(Optional.of("42"), Instant.parse("2026-11-21T15:00:00Z")));

        String skill = jdbc.sql("SELECT content -> 'draft' ->> 'mainSkill' FROM build WHERE id = ?")
                .params(b.id())
                .query(String.class)
                .single();
        assertThat(skill).isEqualTo("Ice Shot");
    }
}
