package com.poe.poe2buildassistant.domain.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ataques ao agregado Catalog (card #3). */
class CatalogChaosTest {

    private final CatalogVersion v = new CatalogVersion(1, "0.4", CatalogVersion.Status.PUBLISHED);

    @Test
    @DisplayName("B7b: nomes que só diferem por espaço interno não viram dois itens no catálogo")
    void quaseDuplicadoPorEspacoInterno() {
        assertThatThrownBy(
                        () ->
                                Catalog.builder(v)
                                        .skillGem(new SkillGem("Ice Shot"))
                                        .skillGem(new SkillGem("Ice  Shot"))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("B7b: espaço não separável também não cria um segundo item")
    void quaseDuplicadoPorNbsp() {
        assertThatThrownBy(
                        () ->
                                Catalog.builder(v)
                                        .supportGem(new SupportGem("Cold Infusion"))
                                        .supportGem(new SupportGem("Cold Infusion"))
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
