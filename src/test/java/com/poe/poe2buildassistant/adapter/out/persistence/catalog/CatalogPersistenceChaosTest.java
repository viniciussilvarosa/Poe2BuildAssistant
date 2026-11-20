package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Ataques à persistência do catálogo (card #4): o banco nunca pode aceitar o que o domínio recusa. */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogPersistenceAdapter.class})
class CatalogPersistenceChaosTest {

    @Autowired private CatalogPersistenceAdapter adapter;
    @Autowired private JdbcClient jdbc;
    @Autowired private EntityManagerFactory emf;

    private long version(String patch, String status) {
        return jdbc.sql("INSERT INTO catalog_version (patch, status) VALUES (?, ?) RETURNING id")
                .params(patch, status)
                .query(Long.class)
                .single();
    }

    private long item(String table, long versionId, String name) {
        return jdbc.sql("INSERT INTO " + table + " (catalog_version_id, name) VALUES (?, ?) RETURNING id")
                .params(versionId, name)
                .query(Long.class)
                .single();
    }

    @Nested
    @DisplayName("D1 — banco recusa o que o domínio considera duplicado")
    class QuaseDuplicados {

        @ParameterizedTest(name = "\"{0}\" ao lado de \"Ice Shot\" é recusado")
        @ValueSource(strings = {"Ice Shot ", " Ice Shot", "Ice  Shot", "Ice Shot", "Ice\tShot", " ICE  SHOT "})
        void variacaoDeEspaco(String nome) {
            long v = version("0.4", "PUBLISHED");
            item("skill_gem", v, "Ice Shot");

            assertThatThrownBy(() -> item("skill_gem", v, nome)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("D2/D3 — nomes vazios nunca entram no banco")
    class NomesVazios {

        @ParameterizedTest(name = "nome vazio em {0} é recusado")
        @ValueSource(strings = {"character_class", "weapon_type", "skill_gem", "support_gem", "passive_notable", "playstyle"})
        void nomeVazio(String tabela) {
            long v = version("0.4", "PUBLISHED");
            assertThatThrownBy(() -> item(tabela, v, "   ")).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("ascendência com nome vazio é recusada")
        void ascendenciaVazia() {
            long v = version("0.4", "PUBLISHED");
            long ranger = item("character_class", v, "Ranger");
            assertThatThrownBy(
                            () ->
                                    jdbc.sql("INSERT INTO ascendancy (catalog_version_id, character_class_id, name) VALUES (?, ?, ?)")
                                            .params(v, ranger, "")
                                            .update())
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("D3: versão com patch vazio é recusada")
        void patchVazio() {
            assertThatThrownBy(() -> version(" ", "DRAFT")).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("D4/D5 — limites e nulos")
    class LimitesENulos {

        @Test
        @DisplayName("D4: nome com mais de 120 caracteres é recusado")
        void nomeLongo() {
            long v = version("0.4", "PUBLISHED");
            assertThatThrownBy(() -> item("skill_gem", v, "x".repeat(121))).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("D5: description NULL explícito é recusado")
        void descricaoNula() {
            long v = version("0.4", "PUBLISHED");
            assertThatThrownBy(
                            () ->
                                    jdbc.sql("INSERT INTO passive_notable (catalog_version_id, name, description) VALUES (?, ?, NULL)")
                                            .params(v, "Swift Flight")
                                            .update())
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("D6 — carga sem N+1")
    class SemNMaisUm {

        @Test
        @DisplayName("catálogo com 4 classes, 40 ascendências e 30 gemas com armas carrega em até 9 comandos SQL")
        void cargaEmConsultasFixas() {
            long v = version("0.4", "PUBLISHED");
            long bow = item("weapon_type", v, "Bow");
            long staff = item("weapon_type", v, "Staff");
            for (int c = 0; c < 4; c++) {
                long cls = item("character_class", v, "Class " + c);
                for (int a = 0; a < 10; a++) {
                    jdbc.sql("INSERT INTO ascendancy (catalog_version_id, character_class_id, name) VALUES (?, ?, ?)")
                            .params(v, cls, "Asc " + c + "-" + a)
                            .update();
                }
            }
            for (int g = 0; g < 30; g++) {
                long gem = item("skill_gem", v, "Gem " + g);
                jdbc.sql("INSERT INTO skill_gem_weapon_type (catalog_version_id, skill_gem_id, weapon_type_id) VALUES (?, ?, ?)")
                        .params(v, gem, g % 2 == 0 ? bow : staff)
                        .update();
            }
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
            stats.clear();

            var catalog = adapter.loadPublished();

            assertThat(catalog.ascendancies()).hasSize(40);
            assertThat(catalog.skillGems()).allSatisfy(g -> assertThat(g.weaponTypes()).hasSize(1));
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(9);
        }
    }

    @Nested
    @DisplayName("D7 — dado inválido que escapou do banco gera erro identificável")
    class DadoInvalido {

        @Test
        @DisplayName("quase-duplicado já gravado (bypass) falha citando o item, não com erro genérico")
        void quaseDuplicadoGravado() {
            long v = version("0.4", "PUBLISHED");
            // Simula dado gravado sem a constraint: o DDL do PostgreSQL é transacional, então o índice
            // volta no rollback do @DataJpaTest.
            jdbc.sql("DROP INDEX uq_support_gem_name").update();
            item("support_gem", v, "Pierce");
            item("support_gem", v, "Pierce ");

            assertThatThrownBy(adapter::loadPublished)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Pierce");
        }
    }
}
