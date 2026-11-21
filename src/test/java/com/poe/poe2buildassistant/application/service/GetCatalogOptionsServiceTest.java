package com.poe.poe2buildassistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase.CatalogOptions;
import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase.ClassOption;
import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase.SkillGemOption;
import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import com.poe.poe2buildassistant.domain.catalog.Playstyle;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetCatalogOptionsServiceTest {

    private static final CharacterClass WITCH = new CharacterClass("Witch");
    private static final CharacterClass RANGER = new CharacterClass("Ranger");
    private static final CharacterClass MONK = new CharacterClass("Monk");

    /** Inserido fora de ordem de propósito: a ordem de cadastro não pode vazar para o formulário. */
    private static Catalog catalog() {
        return Catalog.builder(new CatalogVersion(7, "0.4", CatalogVersion.Status.PUBLISHED))
                .characterClass(WITCH)
                .characterClass(RANGER)
                .characterClass(MONK)
                .ascendancy(new Ascendancy("Pathfinder", RANGER))
                .ascendancy(new Ascendancy("Infernalist", WITCH))
                .ascendancy(new Ascendancy("Deadeye", RANGER))
                .weaponType(new WeaponType("Staff"))
                .weaponType(new WeaponType("Bow"))
                .skillGem(new SkillGem("Ice Shot", List.of("Bow")))
                .skillGem(new SkillGem("Fireball"))
                .playstyle(new Playstyle("Corpo a corpo"))
                .playstyle(new Playstyle("À distância"))
                .build();
    }

    private final GetCatalogOptionsService service = new GetCatalogOptionsService(GetCatalogOptionsServiceTest::catalog);

    @Test
    @DisplayName("ordena opções alfabeticamente")
    void ordenaAlfabeticamente() {
        CatalogOptions options = service.get();

        assertThat(options.classes()).extracting(ClassOption::name).containsExactly("Monk", "Ranger", "Witch");
        assertThat(options.weaponTypes()).containsExactly("Bow", "Staff");
        assertThat(options.skillGems()).extracting(SkillGemOption::name).containsExactly("Fireball", "Ice Shot");
    }

    @Test
    @DisplayName("ordena estilos em pt-BR: 'À distância' fica no A, não depois do Z")
    void ordenaComAcento() {
        assertThat(service.get().playstyles()).containsExactly("À distância", "Corpo a corpo");
    }

    @Test
    @DisplayName("agrupa as ascendências sob a própria classe, em ordem alfabética")
    void agrupaAscendencias() {
        CatalogOptions options = service.get();

        assertThat(options.classes())
                .containsExactly(
                        new ClassOption("Monk", List.of()),
                        new ClassOption("Ranger", List.of("Deadeye", "Pathfinder")),
                        new ClassOption("Witch", List.of("Infernalist")));
    }

    @Test
    @DisplayName("devolve o patch e as armas exigidas por cada gema")
    void patchEArmas() {
        CatalogOptions options = service.get();

        assertThat(options.patch()).isEqualTo("0.4");
        assertThat(options.skillGems())
                .containsExactly(new SkillGemOption("Fireball", List.of()), new SkillGemOption("Ice Shot", List.of("Bow")));
    }

    @Test
    @DisplayName("sem catálogo publicado propaga CatalogNotPublished")
    void semCatalogo() {
        var semPublicado = new GetCatalogOptionsService(() -> {
            throw new CatalogNotPublished();
        });

        assertThatThrownBy(semPublicado::get).isInstanceOf(CatalogNotPublished.class);
    }
}
