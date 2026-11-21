package com.poe.poe2buildassistant.domain.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DraftRepairTest {

    private final Catalog catalog = CatalogFixtures.catalog()
            .with(CatalogItem.supportGem("Biting Frost I"))
            .with(CatalogItem.supportGem("Biting Frost II"));
    private final DraftRepair repair = new DraftRepair();
    private final BuildValidator validator = new BuildValidator();
    private final BuildRequest request = new BuildRequest("arqueiro", Constraints.none());

    private static GeneratedBuildDraft draft(List<String> supports, List<String> notables) {
        return CatalogFixtures.draft("Ranger", "Deadeye", "Bow", "Ice Shot", supports, notables);
    }

    @Test
    @DisplayName("suporte desconhecido sai quando os válidos bastam, e o rascunho passa no validador")
    void descartaDesconhecido() {
        var result = repair.repair(draft(List.of("Pierce", "Added Cold Damage"), CatalogFixtures.FIVE_NOTABLES), catalog);

        assertThat(result.draft().supports()).containsExactly("Pierce");
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(validator.validate(result.draft(), catalog, request)).isEmpty();
    }

    @Test
    @DisplayName("suporte sem nível vira o primeiro nível do catálogo")
    void completaNivel() {
        var result = repair.repair(draft(List.of("biting frost"), CatalogFixtures.FIVE_NOTABLES), catalog);

        assertThat(result.draft().supports()).containsExactly("Biting Frost I");
        assertThat(result.corrected()).isEqualTo(1);
        assertThat(validator.validate(result.draft(), catalog, request)).isEmpty();
    }

    @Test
    @DisplayName("notable inventado sai quando sobram ao menos 3 distintos")
    void descartaNotable() {
        var notables = List.of("Swift Flight", "Cold Precision", "Frozen Edge", "Notable Inventado");

        var result = repair.repair(draft(List.of("Pierce"), notables), catalog);

        assertThat(result.draft().passiveTree().notables()).containsExactly("Swift Flight", "Cold Precision", "Frozen Edge");
        assertThat(validator.validate(result.draft(), catalog, request)).isEmpty();
    }

    @Test
    @DisplayName("sem itens válidos suficientes, nada muda e o validador recusa com UNKNOWN_ITEM")
    void insuficienteContinuaRecusado() {
        var notables = List.of("Swift Flight", "Cold Precision", "Notable Inventado");
        var original = draft(List.of("Fake Support"), notables);

        var result = repair.repair(original, catalog);

        assertThat(result.draft()).isEqualTo(original);
        assertThat(result.changed()).isFalse();
        assertThat(validator.validate(result.draft(), catalog, request))
                .extracting(BuildViolation::type, BuildViolation::itemName)
                .contains(
                        org.assertj.core.groups.Tuple.tuple(BuildViolation.Type.UNKNOWN_ITEM, "Fake Support"),
                        org.assertj.core.groups.Tuple.tuple(BuildViolation.Type.UNKNOWN_ITEM, "Notable Inventado"));
    }

    @Test
    @DisplayName("rascunho já válido sai idêntico")
    void validoIntacto() {
        var original = CatalogFixtures.validDraft();

        assertThat(repair.repair(original, catalog).draft()).isSameAs(original);
    }

    @Test
    @DisplayName("repetição não é escondida: duas grafias do mesmo suporte continuam para o validador")
    void repeticaoContinua() {
        var result = repair.repair(draft(List.of("Biting Frost", "Biting Frost I", "Inventado"), CatalogFixtures.FIVE_NOTABLES), catalog);

        assertThat(validator.validate(result.draft(), catalog, request))
                .extracting(BuildViolation::type)
                .contains(BuildViolation.Type.DUPLICATE_ITEM);
    }
}
