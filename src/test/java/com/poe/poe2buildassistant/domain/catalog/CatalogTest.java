package com.poe.poe2buildassistant.domain.catalog;

import static com.poe.poe2buildassistant.domain.CatalogFixtures.RANGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.domain.CatalogFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogTest {

    private final Catalog catalog = CatalogFixtures.catalog();
    private final CatalogVersion v04 = new CatalogVersion(1, "0.4", CatalogVersion.Status.PUBLISHED);

    @Test
    @DisplayName("busca por nome ignora caixa e espaços nas pontas")
    void buscaNormalizada() {
        assertThat(catalog.findSkillGem("  ICE shot ")).map(SkillGem::name).contains("Ice Shot");
        assertThat(catalog.findSkillGem("Ice")).isEmpty();
    }

    @Test
    @DisplayName("lista as ascendências de uma classe")
    void ascendenciasDaClasse() {
        assertThat(catalog.ascendanciesOf(RANGER))
                .extracting(Ascendancy::name)
                .containsExactlyInAnyOrder("Deadeye", "Pathfinder");
    }

    @Test
    @DisplayName("rejeita nomes duplicados no mesmo tipo")
    void rejeitaDuplicados() {
        assertThatThrownBy(
                        () ->
                                Catalog.builder(v04)
                                        .skillGem(new SkillGem("Ice Shot"))
                                        .skillGem(new SkillGem(" ice shot"))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ice Shot");
    }

    @Test
    @DisplayName("rejeita ascendência cuja classe não está no catálogo")
    void rejeitaAscendenciaOrfa() {
        assertThatThrownBy(() -> Catalog.builder(v04).ascendancy(new Ascendancy("Deadeye", RANGER)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ranger");
    }

    @Test
    @DisplayName("item sem nome é rejeitado na criação")
    void rejeitaNomeVazio() {
        assertThatThrownBy(() -> new SupportGem(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("listas expostas são imutáveis")
    void listasImutaveis() {
        assertThatThrownBy(() -> catalog.skillGems().add(new SkillGem("Hack")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
