package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.catalog.CatalogNames;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.catalog.Playstyle;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.SupportGem;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound;
import com.poe.poe2buildassistant.domain.shared.DraftAlreadyExists;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** Tabela de cada tipo editável; nomes fixos, nunca vindos da requisição. */
    private static final Map<CatalogItem.Type, String> TABLES = Map.of(
            CatalogItem.Type.CLASS, "character_class",
            CatalogItem.Type.ASCENDANCY, "ascendancy",
            CatalogItem.Type.WEAPON_TYPE, "weapon_type",
            CatalogItem.Type.SKILL_GEM, "skill_gem",
            CatalogItem.Type.SUPPORT_GEM, "support_gem",
            CatalogItem.Type.PLAYSTYLE, "playstyle");

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
        Catalog catalog = load(version);
        logFirstLoad(catalog);
        return catalog;
    }

    @Override
    public Catalog loadVersion(long versionId) {
        return load(versions.findById(versionId).orElseThrow(() -> new CatalogVersionNotFound(versionId)));
    }

    private Catalog load(CatalogVersionEntity version) {
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

        return builder.build();
    }

    @Override
    public Optional<CatalogVersion> findVersion(long versionId) {
        return versions.findById(versionId).map(v -> new CatalogVersion(v.getId(), v.getPatch(), v.getStatus()));
    }

    /*
     * Escrita direta em SQL: as entidades do catálogo são só de leitura. Toda escrita começa por
     * lockDraft: o FOR UPDATE trava a versão até o fim da transação, então uma publicação
     * concorrente espera, e uma que veio antes é vista aqui (chaos N2).
     */

    private String lockDraft(long versionId) {
        Map<String, Object> version = jdbc.sql("SELECT patch, status FROM catalog_version WHERE id = ? FOR UPDATE")
                .param(versionId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new CatalogVersionNotFound(versionId));
        String patch = (String) version.get("patch");
        if (!CatalogVersion.Status.DRAFT.name().equals(version.get("status"))) {
            throw new CatalogVersionNotEditable(patch);
        }
        return patch;
    }

    @Override
    public Optional<CatalogVersion> findDraft() {
        return versions.findByStatus(CatalogVersion.Status.DRAFT)
                .map(v -> new CatalogVersion(v.getId(), v.getPatch(), v.getStatus()));
    }

    @Override
    @Transactional
    public CatalogVersion createDraftFromPublished(String patch) {
        Long from = jdbc.sql("SELECT id FROM catalog_version WHERE status = 'PUBLISHED' FOR UPDATE")
                .query(Long.class)
                .optional()
                .orElseThrow(CatalogNotPublished::new);
        // Com a publicada travada, duas criações simultâneas passam por aqui uma de cada vez: a
        // segunda vê o rascunho da primeira e recebe 409, não o 500 do índice único (chaos C1).
        findDraft().ifPresent(d -> {
            throw new DraftAlreadyExists(d.patch());
        });
        long to = jdbc.sql("INSERT INTO catalog_version (patch, status) VALUES (?, 'DRAFT') RETURNING id")
                .param(patch)
                .query(Long.class)
                .single();
        for (String table : List.of("character_class", "weapon_type", "skill_gem", "support_gem", "playstyle")) {
            jdbc.sql("INSERT INTO " + table + " (catalog_version_id, name) SELECT ?, name FROM " + table + " WHERE catalog_version_id = ?")
                    .params(to, from)
                    .update();
        }
        jdbc.sql("""
                        INSERT INTO passive_notable (catalog_version_id, name, description)
                        SELECT ?, name, description FROM passive_notable WHERE catalog_version_id = ?""")
                .params(to, from)
                .update();
        jdbc.sql("""
                        INSERT INTO ascendancy (catalog_version_id, character_class_id, name)
                        SELECT ?, nc.id, a.name
                        FROM ascendancy a
                        JOIN character_class oc ON oc.id = a.character_class_id
                        JOIN character_class nc ON nc.catalog_version_id = ? AND nc.name = oc.name
                        WHERE a.catalog_version_id = ?""")
                .params(to, to, from)
                .update();
        jdbc.sql("""
                        INSERT INTO skill_gem_weapon_type (catalog_version_id, skill_gem_id, weapon_type_id)
                        SELECT ?, ng.id, nw.id
                        FROM skill_gem_weapon_type p
                        JOIN skill_gem og ON og.id = p.skill_gem_id
                        JOIN weapon_type ow ON ow.id = p.weapon_type_id
                        JOIN skill_gem ng ON ng.catalog_version_id = ? AND ng.name = og.name
                        JOIN weapon_type nw ON nw.catalog_version_id = ? AND nw.name = ow.name
                        WHERE p.catalog_version_id = ?""")
                .params(to, to, to, from)
                .update();
        return new CatalogVersion(to, patch, CatalogVersion.Status.DRAFT);
    }

    @Override
    @Transactional
    public void saveItem(long versionId, CatalogItem item) {
        lockDraft(versionId);
        String table = TABLES.get(item.type());
        // Atualiza no lugar quando já existe: apagar e recriar uma classe levaria junto as
        // ascendências dela (ON DELETE CASCADE), e uma arma, as exigências das gemas.
        Optional<Long> existing = idOf(table, versionId, item.name());
        long id = existing.orElseGet(() -> jdbc.sql("INSERT INTO " + table + " (catalog_version_id, name) VALUES (?, ?) RETURNING id")
                        .params(versionId, item.name())
                        .query(Long.class)
                        .single());
        if (existing.isPresent()) {
            jdbc.sql("UPDATE " + table + " SET name = ? WHERE id = ?").params(item.name(), id).update();
        }
        if (item.type() == CatalogItem.Type.ASCENDANCY) {
            long classId = idOf("character_class", versionId, item.characterClass()).orElseThrow();
            jdbc.sql("UPDATE ascendancy SET character_class_id = ? WHERE id = ?").params(classId, id).update();
        }
        if (item.type() == CatalogItem.Type.SKILL_GEM) {
            jdbc.sql("DELETE FROM skill_gem_weapon_type WHERE skill_gem_id = ?").param(id).update();
            for (String weapon : item.weaponTypes()) {
                jdbc.sql("INSERT INTO skill_gem_weapon_type (catalog_version_id, skill_gem_id, weapon_type_id) VALUES (?, ?, ?)")
                        .params(versionId, id, idOf("weapon_type", versionId, weapon).orElseThrow())
                        .update();
            }
        }
    }

    @Override
    @Transactional
    public void removeItem(long versionId, CatalogItem.Type type, String name) {
        lockDraft(versionId);
        idOf(TABLES.get(type), versionId, name)
                .ifPresent(id -> jdbc.sql("DELETE FROM " + TABLES.get(type) + " WHERE id = ?").param(id).update());
    }

    @Override
    @Transactional
    public CatalogVersion publish(long draftId) {
        String patch = lockDraft(draftId);
        // Arquiva antes de publicar: o índice único parcial só aceita uma PUBLISHED por vez.
        jdbc.sql("UPDATE catalog_version SET status = 'ARCHIVED' WHERE status = 'PUBLISHED'").update();
        jdbc.sql("UPDATE catalog_version SET status = 'PUBLISHED', published_at = now() WHERE id = ?").param(draftId).update();
        return new CatalogVersion(draftId, patch, CatalogVersion.Status.PUBLISHED);
    }

    /**
     * Busca pelo nome com a mesma regra do domínio ({@link CatalogNames#normalize}: caixa, espaços e
     * apóstrofos). Com um {@code lower(name)} em SQL, "Alchemist’s Boon" não achava "Alchemist's
     * Boon", virava uma segunda linha e o rascunho deixava de carregar (chaos C2).
     */
    private Optional<Long> idOf(String table, long versionId, String name) {
        String key = CatalogNames.normalize(name);
        return jdbc.sql("SELECT id, name FROM " + table + " WHERE catalog_version_id = ?")
                .param(versionId)
                .query((rs, n) -> CatalogNames.normalize(rs.getString("name")).equals(key) ? rs.getLong("id") : null)
                .list()
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    @Override
    @Transactional
    public void replaceNotables(long versionId, List<PassiveNotable> replacement) {
        lockDraft(versionId);
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
