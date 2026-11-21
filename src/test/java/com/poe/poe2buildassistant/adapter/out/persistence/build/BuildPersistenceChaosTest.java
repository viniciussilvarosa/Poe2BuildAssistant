package com.poe.poe2buildassistant.adapter.out.persistence.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
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
import org.springframework.jdbc.core.simple.JdbcClient;

/** Ataques à persistência de builds (card #10): conteúdo hostil e evolução do formato. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, BuildPersistenceAdapter.class})
class BuildPersistenceChaosTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");

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

    @Test
    @DisplayName("S16: conceito com caractere nulo (\\u0000) não quebra a gravação")
    void conceitoComNul() {
        var request = new BuildRequest("arqueiro\u0000mágico", Constraints.none());
        Build build = new Build(UUID.randomUUID(), Optional.empty(), versionId, request, CatalogFixtures.validDraft(), NOW);

        assertThatCode(() -> adapter.save(build)).doesNotThrowAnyException();
        assertThat(adapter.findById(build.id())).isPresent();
    }

    @Test
    @DisplayName("S17: texto do LLM com caractere nulo não quebra a gravação")
    void draftComNul() {
        GeneratedBuildDraft valido = CatalogFixtures.validDraft();
        GeneratedBuildDraft comNul = new GeneratedBuildDraft(
                "resumo\u0000com nulo", valido.synergy(), valido.choiceReasons(), valido.characterClass(), valido.ascendancy(),
                valido.weaponType(), valido.mainSkill(), valido.supports(), valido.passiveTree(), valido.gear(), valido.campaignTips());
        Build build = new Build(UUID.randomUUID(), Optional.empty(), versionId, new BuildRequest("x", Constraints.none()), comNul, NOW);

        assertThatCode(() -> adapter.save(build)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("S5: linha com campo extra no jsonb (formato futuro) continua legível")
    void campoExtraNoJsonb() {
        Build build = adapter.save(new Build(
                UUID.randomUUID(), Optional.empty(), versionId, new BuildRequest("x", Constraints.none()), CatalogFixtures.validDraft(), NOW));
        jdbc.sql("UPDATE build SET content = content || '{\"futureField\": {\"a\": 1}}'::jsonb WHERE id = ?")
                .params(build.id())
                .update();

        assertThat(adapter.findById(build.id())).map(b -> b.content().mainSkill()).contains("Ice Shot");
    }
}
