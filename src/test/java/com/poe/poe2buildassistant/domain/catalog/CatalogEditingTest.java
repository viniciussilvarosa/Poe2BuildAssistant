package com.poe.poe2buildassistant.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.domain.CatalogFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Edição do catálogo pelo admin: cada mudança gera um catálogo novo que ainda respeita as invariantes. */
class CatalogEditingTest {

    private final Catalog catalog = CatalogFixtures.catalog();

    @Test
    @DisplayName("adicionar gema devolve catálogo novo com ela; o original não muda")
    void adicionaGema() {
        Catalog edited = catalog.with(CatalogItem.skillGem("Nova Gem", List.of("Bow")));

        assertThat(edited.findSkillGem("Nova Gem")).isPresent();
        assertThat(catalog.findSkillGem("Nova Gem")).isEmpty();
    }

    @Test
    @DisplayName("salvar item com nome existente substitui (ex.: trocar as armas de uma gema)")
    void substitui() {
        Catalog edited = catalog.with(CatalogItem.skillGem("Ice Shot", List.of("Staff")));

        assertThat(edited.findSkillGem("Ice Shot")).hasValueSatisfying(g -> assertThat(g.weaponTypes()).containsExactly("Staff"));
        assertThat(edited.skillGems()).hasSameSizeAs(catalog.skillGems());
    }

    @Test
    @DisplayName("ascendência sem classe existente é recusada")
    void ascendenciaSemClasse() {
        assertThatThrownBy(() -> catalog.with(CatalogItem.ascendancy("Shaman", "Druid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Druid");
    }

    @Test
    @DisplayName("remover classe que ainda tem ascendências é recusado")
    void removeClasseEmUso() {
        assertThatThrownBy(() -> catalog.without(CatalogItem.Type.CLASS, "Ranger")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("remover arma exigida por uma gema é recusado")
    void removeArmaEmUso() {
        assertThatThrownBy(() -> catalog.without(CatalogItem.Type.WEAPON_TYPE, "Bow")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("remover item inexistente é recusado com o nome")
    void removeInexistente() {
        assertThatThrownBy(() -> catalog.without(CatalogItem.Type.PLAYSTYLE, "Nada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nada");
    }

    @Test
    @DisplayName("remover suporte tira só ele")
    void removeSuporte() {
        Catalog edited = catalog.without(CatalogItem.Type.SUPPORT_GEM, "pierce");

        assertThat(edited.findSupportGem("Pierce")).isEmpty();
        assertThat(edited.supportGems()).hasSize(catalog.supportGems().size() - 1);
    }
}
