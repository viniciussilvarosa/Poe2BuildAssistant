package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.SupportGem;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Ataques à carga inicial do catálogo (card #5, PR #34). */
class CatalogSeedChaosTest {

    /** Mesmo limite de app.generation.slice-max-items: o que o LLM realmente vê do catálogo. */
    private static final int SLICE_MAX = 60;

    @Nested
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({TestcontainersConfiguration.class, CatalogPersistenceAdapter.class})
    @DisplayName("S1 — o recorte enviado à IA com o catálogo real")
    class RecorteComCatalogoReal {

        @Autowired private CatalogPersistenceAdapter adapter;
        private Catalog catalog;

        @BeforeEach
        void carregar() {
            catalog = adapter.loadPublished();
        }

        private CatalogSlice slice(Constraints constraints) {
            return CatalogSlice.of(catalog, new BuildRequest("arqueiro que congela tudo", constraints), SLICE_MAX);
        }

        @Test
        @DisplayName("com Bow fixo, toda gema de arco chega à IA (Ice Shot inclusive)")
        void todasAsGemasDeArco() {
            List<String> deArco = catalog.skillGems().stream()
                    .filter(g -> g.weaponTypes().contains("Bow"))
                    .map(SkillGem::name)
                    .toList();

            List<String> vistas = slice(Constraints.none().withWeaponType("Bow")).skillGems().stream()
                    .map(SkillGem::name)
                    .toList();

            assertThat(vistas).contains("Ice Shot").containsAll(deArco);
        }

        @Test
        @DisplayName("sem escolhas, os suportes oferecidos não param no começo do alfabeto")
        void suportesDeTodoOAlfabeto() {
            List<String> suportes = slice(Constraints.none()).supportGems().stream().map(SupportGem::name).toList();

            assertThat(suportes.stream().map(n -> n.charAt(0)).distinct().count())
                    .as("iniciais dos suportes vistos pela IA: %s", suportes)
                    .isGreaterThanOrEqualTo(10);
        }

        @Test
        @DisplayName("sem escolhas, a IA vê habilidades de várias armas, não só as de A a D")
        void habilidadesDeVariasArmas() {
            List<SkillGem> vistas = slice(Constraints.none()).skillGems();

            assertThat(vistas.stream().flatMap(g -> g.weaponTypes().stream()).distinct().count())
                    .as("armas cobertas pelas habilidades vistas")
                    .isGreaterThanOrEqualTo(8);
            assertThat(vistas.stream().map(g -> g.name().charAt(0)).distinct().count()).isGreaterThanOrEqualTo(10);
        }

        @Test
        @DisplayName("apóstrofo tipográfico do LLM (’) ainda acha a gema com apóstrofo reto")
        void apostrofoTipografico() {
            assertThat(catalog.findSkillGem("Alchemist’s Boon")).isPresent();
        }
    }

    @Nested
    @DisplayName("S2 — migration num banco que já tem catálogo publicado")
    class BancoComCatalogo {

        @Test
        @DisplayName("uma versão publicada à mão antes da V5 não impede o app de subir")
        void publicadoAntes() {
            try (var postgres = new PostgreSQLContainer("postgres:18-alpine")) {
                postgres.start();
                Flyway ate4 = Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .target("4")
                        .load();
                ate4.migrate();
                JdbcClient.create(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()))
                        .sql("INSERT INTO catalog_version (patch, status) VALUES ('0.4', 'PUBLISHED')")
                        .update();

                Flyway todas = Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .load();

                assertThatCode(todas::migrate).doesNotThrowAnyException();
            }
        }
    }
}
