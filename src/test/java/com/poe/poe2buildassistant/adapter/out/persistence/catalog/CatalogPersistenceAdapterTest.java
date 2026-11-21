package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogPersistenceAdapter.class})
class CatalogPersistenceAdapterTest {

    @Autowired private CatalogPersistenceAdapter adapter;
    @Autowired private JdbcClient jdbc;

    /** A carga inicial (V5) já publica o patch 0.5; cada teste monta o próprio catálogo do zero. */
    @BeforeEach
    void semCargaInicial() {
        jdbc.sql("DELETE FROM catalog_version").update();
    }

    private long version(String patch, String status) {
        return jdbc.sql("INSERT INTO catalog_version (patch, status) VALUES (?, ?) RETURNING id")
                .params(patch, status)
                .query(Long.class)
                .single();
    }

    private long item(String table, long versionId, String name) {
        return jdbc.sql("INSERT INTO " + table + " (catalog_version_id, name) VALUES (?, ?) RETURNING id")
                .params(versionId, name)
                .query(Long.class)
                .single();
    }

    /** Versão 0.4 publicada: Ranger→Deadeye, Bow, Ice Shot (exige Bow), Pierce, 1 notable, 1 estilo. */
    private long publishedV04() {
        long v = version("0.4", "PUBLISHED");
        long ranger = item("character_class", v, "Ranger");
        jdbc.sql("INSERT INTO ascendancy (catalog_version_id, character_class_id, name) VALUES (?, ?, ?)")
                .params(v, ranger, "Deadeye")
                .update();
        long bow = item("weapon_type", v, "Bow");
        item("weapon_type", v, "Staff");
        long iceShot = item("skill_gem", v, "Ice Shot");
        item("skill_gem", v, "Fireball");
        jdbc.sql("INSERT INTO skill_gem_weapon_type (catalog_version_id, skill_gem_id, weapon_type_id) VALUES (?, ?, ?)")
                .params(v, iceShot, bow)
                .update();
        item("support_gem", v, "Pierce");
        jdbc.sql("INSERT INTO passive_notable (catalog_version_id, name, description) VALUES (?, ?, ?)")
                .params(v, "Swift Flight", "+10% velocidade de projétil")
                .update();
        item("playstyle", v, "Ranged");
        return v;
    }

    @Test
    @DisplayName("carrega a versão PUBLISHED como agregado Catalog completo")
    void carregaVersaoPublicada() {
        publishedV04();

        Catalog catalog = adapter.loadPublished();

        assertThat(catalog.version().patch()).isEqualTo("0.4");
        assertThat(catalog.findAscendancy("Deadeye")).map(Ascendancy::characterClass).map(c -> c.name()).contains("Ranger");
        assertThat(catalog.findSupportGem("Pierce")).isPresent();
        assertThat(catalog.findWeaponType("Bow")).isPresent();
        assertThat(catalog.findPlaystyle("Ranged")).isPresent();
        assertThat(catalog.findNotable("Swift Flight")).map(PassiveNotable::description).contains("+10% velocidade de projétil");
    }

    @Test
    @DisplayName("carrega a relação gema → tipos de arma (Ice Shot só com Bow; Fireball sem exigência)")
    void carregaArmasDasGemas() {
        publishedV04();

        Catalog catalog = adapter.loadPublished();

        assertThat(catalog.findSkillGem("Ice Shot")).map(SkillGem::weaponTypes).contains(List.of("Bow"));
        assertThat(catalog.findSkillGem("Fireball")).map(SkillGem::weaponTypes).contains(List.of());
    }

    @Test
    @DisplayName("ignora itens de versões DRAFT")
    void ignoraDraft() {
        publishedV04();
        long draft = version("0.5", "DRAFT");
        item("skill_gem", draft, "Draft Gem");

        assertThat(adapter.loadPublished().findSkillGem("Draft Gem")).isEmpty();
    }

    @Test
    @DisplayName("sem versão publicada lança CatalogNotPublished")
    void semVersaoPublicada() {
        version("0.5", "DRAFT");

        assertThatThrownBy(adapter::loadPublished).isInstanceOf(CatalogNotPublished.class);
    }

    @Test
    @DisplayName("banco impede duas versões PUBLISHED")
    void duasPublicadas() {
        version("0.4", "PUBLISHED");

        assertThatThrownBy(() -> version("0.5", "PUBLISHED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("nome de gema é único dentro da mesma versão, ignorando caixa")
    void nomeUnicoNaVersao() {
        long v = version("0.4", "PUBLISHED");
        item("skill_gem", v, "Ice Shot");

        assertThatThrownBy(() -> item("skill_gem", v, "ice shot")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o mesmo nome pode existir em versões diferentes")
    void mesmoNomeEmOutraVersao() {
        long v4 = version("0.4", "PUBLISHED");
        long v5 = version("0.5", "DRAFT");
        item("skill_gem", v4, "Ice Shot");

        assertThat(item("skill_gem", v5, "Ice Shot")).isPositive();
    }

    @Test
    @DisplayName("banco impede ascendência ligada a uma classe de outra versão")
    void ascendenciaDeOutraVersao() {
        long v4 = version("0.4", "PUBLISHED");
        long v5 = version("0.5", "DRAFT");
        long rangerV4 = item("character_class", v4, "Ranger");

        assertThatThrownBy(
                        () ->
                                jdbc.sql("INSERT INTO ascendancy (catalog_version_id, character_class_id, name) VALUES (?, ?, ?)")
                                        .params(v5, rangerV4, "Deadeye")
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("banco impede gema exigindo arma de outra versão")
    void armaDeOutraVersao() {
        long v4 = version("0.4", "PUBLISHED");
        long v5 = version("0.5", "DRAFT");
        long bowV4 = item("weapon_type", v4, "Bow");
        long gemV5 = item("skill_gem", v5, "Ice Shot");

        assertThatThrownBy(
                        () ->
                                jdbc.sql("INSERT INTO skill_gem_weapon_type (catalog_version_id, skill_gem_id, weapon_type_id) VALUES (?, ?, ?)")
                                        .params(v5, gemV5, bowV4)
                                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("status fora do domínio é recusado pelo banco")
    void statusInvalido() {
        assertThatThrownBy(() -> version("0.4", "LIVE")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
