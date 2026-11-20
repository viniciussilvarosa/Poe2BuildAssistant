package com.poe.poe2buildassistant.domain.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BuildRequestTest {

    private final Catalog catalog = CatalogFixtures.catalog();

    @ParameterizedTest(name = "conceito \"{0}\" é rejeitado")
    @NullSource
    @ValueSource(strings = {"", "  ", "\t\n"})
    @DisplayName("rejeita conceito vazio ou só espaços")
    void rejeitaConceitoVazio(String concept) {
        assertThatThrownBy(() -> new BuildRequest(concept, Constraints.none()))
                .isInstanceOf(InvalidBuildRequest.class)
                .hasMessageContaining("conceito");
    }

    @Test
    @DisplayName("guarda o conceito sem espaços nas pontas")
    void normalizaConceito() {
        assertThat(new BuildRequest("  arqueiro mágico ", Constraints.none()).concept())
                .isEqualTo("arqueiro mágico");
    }

    @Test
    @DisplayName("rejeita ascendência que não pertence à classe escolhida")
    void rejeitaAscendenciaDeOutraClasse() {
        var request =
                new BuildRequest(
                        "arqueiro mágico",
                        Constraints.none().withCharacterClass("Ranger").withAscendancy("Infernalist"));

        assertThatThrownBy(() -> request.validateAgainst(catalog))
                .isInstanceOf(InvalidBuildRequest.class)
                .hasMessageContaining("Infernalist")
                .hasMessageContaining("Ranger");
    }

    @Test
    @DisplayName("rejeita restrição que não existe no catálogo")
    void rejeitaRestricaoForaDoCatalogo() {
        var request = new BuildRequest("x", Constraints.none().withWeaponType("Laser Rifle"));

        assertThatThrownBy(() -> request.validateAgainst(catalog))
                .isInstanceOf(InvalidBuildRequest.class)
                .hasMessageContaining("Laser Rifle");
    }

    @Test
    @DisplayName("aceita só o conceito, sem restrições")
    void aceitaSoConceito() {
        assertThatCode(() -> new BuildRequest("arqueiro mágico", Constraints.none()).validateAgainst(catalog))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("aceita restrições válidas comparando sem caixa e espaços")
    void aceitaRestricoesValidas() {
        var constraints =
                Constraints.none()
                        .withCharacterClass(" ranger")
                        .withAscendancy("DEADEYE")
                        .withWeaponType("Bow")
                        .withMainSkill("Ice Shot")
                        .withPlaystyle("Ranged");

        assertThatCode(() -> new BuildRequest("x", constraints).validateAgainst(catalog))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("restrição em branco equivale a 'Deixar a IA escolher'")
    void restricaoEmBrancoEhAusente() {
        assertThat(Constraints.none().withCharacterClass("  ").characterClass()).isEmpty();
    }
}
