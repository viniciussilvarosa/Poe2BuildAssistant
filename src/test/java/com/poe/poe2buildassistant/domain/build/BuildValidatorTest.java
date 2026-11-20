package com.poe.poe2buildassistant.domain.build;

import static com.poe.poe2buildassistant.domain.CatalogFixtures.FIVE_NOTABLES;
import static com.poe.poe2buildassistant.domain.CatalogFixtures.draft;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.BuildViolation.Type;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildValidatorTest {

    private final Catalog catalog = CatalogFixtures.catalog();
    private final BuildValidator validator = new BuildValidator();
    private final BuildRequest rangerBow =
            new BuildRequest("arqueiro mágico", Constraints.none().withCharacterClass("Ranger").withWeaponType("Bow"));

    @Test
    @DisplayName("aprova draft que só cita itens do catálogo e respeita restrições")
    void aprovaDraftCoerente() {
        assertThat(validator.validate(CatalogFixtures.validDraft(), catalog, rangerBow)).isEmpty();
    }

    @Test
    @DisplayName("aponta suporte inexistente no catálogo")
    void apontaSuporteInexistente() {
        var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce", "Fake Support"), FIVE_NOTABLES);

        assertThat(validator.validate(draft, catalog, rangerBow))
                .extracting(BuildViolation::itemName)
                .containsExactly("Fake Support");
    }

    @Test
    @DisplayName("aponta classe diferente da restrição do usuário")
    void apontaClasseDiferenteDaRestricao() {
        var draft = draft("Witch", "Infernalist", "Bow", "Ice Shot", List.of("Pierce"), FIVE_NOTABLES);

        assertThat(validator.validate(draft, catalog, rangerBow))
                .extracting(BuildViolation::type, BuildViolation::field)
                .contains(tuple(Type.CONSTRAINT_IGNORED, "class"));
    }

    @Test
    @DisplayName("aponta notables fora do intervalo 3–8")
    void apontaPoucosNotables() {
        var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"), FIVE_NOTABLES.subList(0, 2));

        assertThat(validator.validate(draft, catalog, rangerBow))
                .extracting(BuildViolation::type)
                .containsExactly(Type.NOTABLE_COUNT);
    }

    @Test
    @DisplayName("aponta mais de 8 notables")
    void apontaNotablesDemais() {
        List<String> nine = List.of("N1", "N2", "N3", "N4", "N5", "N6", "N7", "N8", "N9");
        var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"), nine);

        assertThat(validator.validate(draft, CatalogFixtures.catalogWithNotables(nine), rangerBow))
                .extracting(BuildViolation::type)
                .containsExactly(Type.NOTABLE_COUNT);
    }

    @Test
    @DisplayName("comparação de nomes ignora caixa e espaços nas pontas")
    void comparacaoNormalizada() {
        var draft = draft(" ranger", "DEADEYE ", "bow", " ice shot", List.of("pierce "), FIVE_NOTABLES);

        assertThat(validator.validate(draft, catalog, rangerBow)).isEmpty();
    }

    @Test
    @DisplayName("aponta ascendência de outra classe no draft")
    void apontaAscendenciaIncompativel() {
        var draft = draft("Ranger", "Infernalist", "Bow", "Ice Shot", List.of("Pierce"), FIVE_NOTABLES);

        assertThat(validator.validate(draft, catalog, rangerBow))
                .extracting(BuildViolation::type, BuildViolation::itemName)
                .containsExactly(tuple(Type.ASCENDANCY_MISMATCH, "Infernalist"));
    }

    @Test
    @DisplayName("aponta gema principal, arma e notable inexistentes, cada um no seu campo")
    void apontaItensInexistentes() {
        var notables = new java.util.ArrayList<>(FIVE_NOTABLES);
        notables.set(0, "Imaginary Notable");
        var draft = draft("Ranger", "Deadeye", "Bow", "Ice Nova Shot", List.of("Pierce"), notables);

        assertThat(validator.validate(draft, catalog, rangerBow))
                .extracting(BuildViolation::type, BuildViolation::field, BuildViolation::itemName)
                .containsExactlyInAnyOrder(
                        tuple(Type.UNKNOWN_ITEM, "mainSkill", "Ice Nova Shot"),
                        tuple(Type.UNKNOWN_ITEM, "notables", "Imaginary Notable"));
    }

    @Test
    @DisplayName("campo obrigatório ausente no draft é uma violação, não uma exceção")
    void campoAusente() {
        var draft = draft(null, "Deadeye", "  ", "Ice Shot", List.of("Pierce"), FIVE_NOTABLES);

        assertThat(validator.validate(draft, catalog, new BuildRequest("x", Constraints.none())))
                .extracting(BuildViolation::type, BuildViolation::field)
                .containsExactlyInAnyOrder(
                        tuple(Type.MISSING_FIELD, "class"), tuple(Type.MISSING_FIELD, "weaponType"));
    }

    @Test
    @DisplayName("aponta skill que não pode ser usada com a arma do draft")
    void apontaSkillIncompativelComArma() {
        var draft = draft("Witch", "Infernalist", "Staff", "Ice Shot", List.of("Pierce"), FIVE_NOTABLES);

        assertThat(validator.validate(draft, catalog, new BuildRequest("x", Constraints.none())))
                .extracting(BuildViolation::type, BuildViolation::field, BuildViolation::itemName)
                .containsExactly(tuple(Type.SKILL_WEAPON_MISMATCH, "mainSkill", "Ice Shot"));
    }

    @Test
    @DisplayName("sem restrições, qualquer combinação coerente do catálogo é aceita")
    void semRestricoes() {
        var draft = draft("Witch", "Infernalist", "Staff", "Fireball", List.of("Elemental Focus"), FIVE_NOTABLES);

        assertThat(validator.validate(draft, catalog, new BuildRequest("x", Constraints.none()))).isEmpty();
    }
}
