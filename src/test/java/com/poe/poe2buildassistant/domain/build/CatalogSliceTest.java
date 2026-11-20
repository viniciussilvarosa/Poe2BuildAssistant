package com.poe.poe2buildassistant.domain.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogSliceTest {

    private final Catalog catalog = CatalogFixtures.catalog();

    private static BuildRequest request(Constraints constraints) {
        return new BuildRequest("arqueiro mágico", constraints);
    }

    @Test
    @DisplayName("com classe escolhida inclui só ascendências daquela classe")
    void classeEscolhida() {
        var slice = CatalogSlice.of(catalog, request(Constraints.none().withCharacterClass("Ranger")), 50);

        assertThat(slice.classes()).extracting(CharacterClass::name).containsExactly("Ranger");
        assertThat(slice.ascendancies()).extracting(Ascendancy::name).containsExactlyInAnyOrder("Deadeye", "Pathfinder");
    }

    @Test
    @DisplayName("sem restrições inclui todas as classes e respeita o limite de itens por tipo")
    void semRestricoesComLimite() {
        Catalog grande = CatalogFixtures.catalogWithNotables(IntStream.rangeClosed(1, 30).mapToObj(i -> "N" + i).toList());

        var slice = CatalogSlice.of(grande, request(Constraints.none()), 10);

        assertThat(slice.classes()).hasSize(grande.classes().size());
        assertThat(slice.notables()).hasSize(10);
        assertThat(List.of(slice.classes(), slice.ascendancies(), slice.weaponTypes(), slice.skillGems(), slice.supportGems(), slice.notables()))
                .allSatisfy(l -> assertThat(l.size()).isLessThanOrEqualTo(10));
    }

    @Test
    @DisplayName("ascendância escolhida sem classe traz a classe dela")
    void ascendenciaSemClasse() {
        var slice = CatalogSlice.of(catalog, request(Constraints.none().withAscendancy("Infernalist")), 50);

        assertThat(slice.classes()).extracting(CharacterClass::name).containsExactly("Witch");
        assertThat(slice.ascendancies()).extracting(Ascendancy::name).containsExactly("Infernalist");
    }

    @Test
    @DisplayName("arma escolhida limita as skills às compatíveis com ela")
    void armaLimitaSkills() {
        var slice = CatalogSlice.of(catalog, request(Constraints.none().withWeaponType("Staff")), 50);

        assertThat(slice.weaponTypes()).extracting(WeaponType::name).containsExactly("Staff");
        assertThat(slice.skillGems()).extracting(SkillGem::name).containsExactly("Fireball");
    }

    @Test
    @DisplayName("skill escolhida limita as armas às que ela exige")
    void skillLimitaArmas() {
        var slice = CatalogSlice.of(catalog, request(Constraints.none().withMainSkill("Ice Shot")), 50);

        assertThat(slice.skillGems()).extracting(SkillGem::name).containsExactly("Ice Shot");
        assertThat(slice.weaponTypes()).extracting(WeaponType::name).containsExactly("Bow");
    }

    @Test
    @DisplayName("limite menor que o máximo de notables é recusado")
    void limiteMuitoPequeno() {
        assertThatThrownBy(() -> CatalogSlice.of(catalog, request(Constraints.none()), 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
