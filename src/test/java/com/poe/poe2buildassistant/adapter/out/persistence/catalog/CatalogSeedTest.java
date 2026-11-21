package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogNames;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.Playstyle;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** Carga inicial do catálogo (card #5): o que as migrations deixam publicado num banco novo. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogPersistenceAdapter.class})
class CatalogSeedTest {

    @Autowired private CatalogPersistenceAdapter adapter;
    private Catalog catalog;

    @BeforeEach
    void carregar() {
        catalog = adapter.loadPublished();
    }

    @Test
    @DisplayName("após migrations existe exatamente uma versão PUBLISHED com o patch vigente")
    void versaoPublicada() {
        assertThat(catalog.version().patch()).isEqualTo("0.5");
        assertThat(catalog.version().status()).isEqualTo(CatalogVersion.Status.PUBLISHED);
    }

    @Test
    @DisplayName("as 8 classes do PoE 2 estão presentes e cada uma tem ao menos uma ascendência")
    void classesComAscendencia() {
        assertThat(catalog.classes())
                .extracting(c -> c.name())
                .containsExactlyInAnyOrder("Druid", "Huntress", "Mercenary", "Monk", "Ranger", "Sorceress", "Warrior", "Witch");
        assertThat(catalog.classes()).allSatisfy(c -> assertThat(catalog.ascendanciesOf(c)).isNotEmpty());
    }

    @Test
    @DisplayName("estilos de jogo contêm a lista fechada aprovada")
    void estilos() {
        assertThat(catalog.playstyles())
                .extracting(Playstyle::name)
                .containsExactlyInAnyOrder(
                        "Corpo a corpo", "À distância", "Magia", "Invocação", "Dano ao longo do tempo", "Tanque");
    }

    @Test
    @DisplayName("exemplo do protótipo é válido: Ranger/Deadeye/Bow/Ice Shot/Pierce I")
    void exemploDoPrototipo() {
        assertThat(catalog.findClass("Ranger")).isPresent();
        assertThat(catalog.findAscendancy("Deadeye")).hasValueSatisfying(a -> assertThat(a.characterClass().name()).isEqualTo("Ranger"));
        WeaponType bow = catalog.findWeaponType("Bow").orElseThrow();
        assertThat(catalog.findSkillGem("Ice Shot")).hasValueSatisfying(g -> assertThat(g.usableWith(bow)).isTrue());
        assertThat(catalog.findSupportGem("Pierce I")).isPresent();
    }

    @Test
    @DisplayName("catálogo completo: centenas de gemas de habilidade e de suporte")
    void catalogoCompleto() {
        assertThat(catalog.skillGems()).hasSizeGreaterThan(200);
        assertThat(catalog.supportGems()).hasSizeGreaterThan(400);
    }

    @Test
    @DisplayName("gemas que exigem arma apontam só para tipos do catálogo, e escudo continua exigindo escudo")
    void armasDasGemas() {
        assertThat(catalog.findSkillGem("Shield Charge"))
                .hasValueSatisfying(g -> assertThat(g.weaponTypes()).containsExactly("Shield"));
        assertThat(catalog.findSkillGem("Fireball")).hasValueSatisfying(g -> assertThat(g.weaponTypes()).isEmpty());
    }

    @Test
    @DisplayName("nenhum nome com espaços nas pontas ou duplicado por caixa")
    void nomesNormalizados() {
        unicos(catalog.classes(), c -> c.name());
        unicos(catalog.ascendancies(), a -> a.name());
        unicos(catalog.weaponTypes(), WeaponType::name);
        unicos(catalog.skillGems(), g -> g.name());
        unicos(catalog.supportGems(), g -> g.name());
        unicos(catalog.playstyles(), Playstyle::name);
    }

    private static <T> void unicos(List<T> items, Function<T, String> name) {
        List<String> names = items.stream().map(name).toList();
        assertThat(names).allSatisfy(n -> assertThat(n).isEqualTo(n.strip()));
        assertThat(names.stream().map(CatalogNames::normalize).distinct()).hasSameSizeAs(names);
    }
}
