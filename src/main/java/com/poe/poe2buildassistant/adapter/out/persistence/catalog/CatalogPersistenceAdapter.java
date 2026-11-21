package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.catalog.Playstyle;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.SupportGem;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega a versão publicada do catálogo em 9 consultas fixas (versão + uma por tabela), sem N+1,
 * e monta o agregado {@link Catalog} do domínio. Entidades JPA não saem deste pacote.
 */
@Component
@Transactional(readOnly = true)
class CatalogPersistenceAdapter implements CatalogRepositoryPort, CatalogEditorPort {

    private static final Logger log = LoggerFactory.getLogger(CatalogPersistenceAdapter.class);

    private final CatalogVersionRepository versions;
    private final CharacterClassRepository classes;
    private final AscendancyRepository ascendancies;
    private final WeaponTypeRepository weaponTypes;
    private final SkillGemRepository skillGems;
    private final SkillGemWeaponTypeRepository skillGemWeaponTypes;
    private final SupportGemRepository supportGems;
    private final PassiveNotableRepository notables;
    private final PlaystyleRepository playstyles;
    private final JdbcClient jdbc;

    /** Versão cuja carga já foi logada: o log de INFO sai só na primeira carga de cada versão. */
    private final AtomicLong lastLoggedVersion = new AtomicLong(-1);

    CatalogPersistenceAdapter(
            CatalogVersionRepository versions,
            CharacterClassRepository classes,
            AscendancyRepository ascendancies,
            WeaponTypeRepository weaponTypes,
            SkillGemRepository skillGems,
            SkillGemWeaponTypeRepository skillGemWeaponTypes,
            SupportGemRepository supportGems,
            PassiveNotableRepository notables,
            PlaystyleRepository playstyles,
            JdbcClient jdbc) {
        this.versions = versions;
        this.classes = classes;
        this.ascendancies = ascendancies;
        this.weaponTypes = weaponTypes;
        this.skillGems = skillGems;
        this.skillGemWeaponTypes = skillGemWeaponTypes;
        this.supportGems = supportGems;
        this.notables = notables;
        this.playstyles = playstyles;
        this.jdbc = jdbc;
    }

    @Override
    public Catalog loadPublished() {
        CatalogVersionEntity version =
                versions.findByStatus(CatalogVersion.Status.PUBLISHED)
                        .orElseThrow(
                                () -> {
                                    log.atError().setMessage("catalog.not_published").log();
                                    return new CatalogNotPublished();
                                });
        Long id = version.getId();

        Map<Long, CharacterClass> classById = byId(classes.findByCatalogVersionIdOrderByNameAsc(id), e -> new CharacterClass(e.getName()));
        Map<Long, WeaponType> weaponById = byId(weaponTypes.findByCatalogVersionIdOrderByNameAsc(id), e -> new WeaponType(e.getName()));
        Map<Long, List<String>> weaponsByGem =
                skillGemWeaponTypes.findByCatalogVersionId(id).stream()
                        .collect(groupingBy(
                                SkillGemWeaponTypeEntity::getSkillGemId,
                                mapping(r -> weaponById.get(r.getWeaponTypeId()).name(), toList())));

        Catalog.Builder builder =
                Catalog.builder(new CatalogVersion(id, version.getPatch(), version.getStatus()));
        classById.values().forEach(builder::characterClass);
        weaponById.values().forEach(builder::weaponType);
        ascendancies.findByCatalogVersionIdOrderByNameAsc(id)
                .forEach(e -> builder.ascendancy(new Ascendancy(e.getName(), classById.get(e.getCharacterClassId()))));
        skillGems.findByCatalogVersionIdOrderByNameAsc(id)
                .forEach(e -> builder.skillGem(new SkillGem(e.getName(), weaponsByGem.getOrDefault(e.getId(), List.of()))));
        supportGems.findByCatalogVersionIdOrderByNameAsc(id).forEach(e -> builder.supportGem(new SupportGem(e.getName())));
        notables.findByCatalogVersionIdOrderByNameAsc(id)
                .forEach(e -> builder.notable(new PassiveNotable(e.getName(), e.getDescription())));
        playstyles.findByCatalogVersionIdOrderByNameAsc(id).forEach(e -> builder.playstyle(new Playstyle(e.getName())));

        Catalog catalog = builder.build();
        logFirstLoad(catalog);
        return catalog;
    }

    @Override
    public Optional<CatalogVersion> findVersion(long versionId) {
        return versions.findById(versionId).map(v -> new CatalogVersion(v.getId(), v.getPatch(), v.getStatus()));
    }

    /** Escrita direta em SQL: as entidades do catálogo são só de leitura. */
    @Override
    @Transactional
    public void replaceNotables(long versionId, List<PassiveNotable> replacement) {
        jdbc.sql("DELETE FROM passive_notable WHERE catalog_version_id = ?").param(versionId).update();
        for (PassiveNotable n : replacement) {
            jdbc.sql("INSERT INTO passive_notable (catalog_version_id, name, description) VALUES (?, ?, ?)")
                    .params(versionId, n.name(), n.description())
                    .update();
        }
    }

    private static <E extends CatalogItemEntity, T> Map<Long, T> byId(List<E> entities, Function<E, T> toDomain) {
        Map<Long, T> map = new LinkedHashMap<>();
        entities.forEach(e -> map.put(e.getId(), toDomain.apply(e)));
        return map;
    }

    private void logFirstLoad(Catalog catalog) {
        long id = catalog.version().id();
        if (lastLoggedVersion.getAndSet(id) != id) {
            log.atInfo()
                    .setMessage("catalog.loaded")
                    .addKeyValue("patch", catalog.version().patch())
                    .addKeyValue("classes", catalog.classes().size())
                    .addKeyValue("ascendancies", catalog.ascendancies().size())
                    .addKeyValue("weaponTypes", catalog.weaponTypes().size())
                    .addKeyValue("skillGems", catalog.skillGems().size())
                    .addKeyValue("supportGems", catalog.supportGems().size())
                    .addKeyValue("notables", catalog.notables().size())
                    .addKeyValue("playstyles", catalog.playstyles().size())
                    .log();
        }
    }
}
