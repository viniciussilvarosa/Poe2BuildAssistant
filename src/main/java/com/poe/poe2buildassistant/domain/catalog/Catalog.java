package com.poe.poe2buildassistant.domain.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Catálogo de uma versão do jogo: a única fonte de verdade sobre quais classes, ascendências,
 * armas, gemas, notables e estilos existem. Buscas ignoram caixa e espaços nas pontas.
 *
 * <p>Invariantes: nomes únicos por tipo, toda ascendência pertence a uma classe do catálogo e toda
 * arma exigida por uma gema existe no catálogo.
 */
public final class Catalog {

    private final CatalogVersion version;
    private final Map<String, CharacterClass> classes;
    private final Map<String, Ascendancy> ascendancies;
    private final Map<String, WeaponType> weaponTypes;
    private final Map<String, SkillGem> skillGems;
    private final Map<String, SupportGem> supportGems;
    private final Map<String, PassiveNotable> notables;
    private final Map<String, Playstyle> playstyles;

    private Catalog(Builder b) {
        this.version = Objects.requireNonNull(b.version, "catálogo sem versão");
        this.classes = index(b.classes, CharacterClass::name, "classe");
        this.ascendancies = index(b.ascendancies, Ascendancy::name, "ascendência");
        this.weaponTypes = index(b.weaponTypes, WeaponType::name, "tipo de arma");
        this.skillGems = index(b.skillGems, SkillGem::name, "gema de habilidade");
        this.supportGems = index(b.supportGems, SupportGem::name, "gema de suporte");
        this.notables = index(b.notables, PassiveNotable::name, "notable");
        this.playstyles = index(b.playstyles, Playstyle::name, "estilo de jogo");
        ascendancies.values().stream()
                .filter(a -> !classes.containsKey(CatalogNames.normalize(a.characterClass().name())))
                .findFirst()
                .ifPresent(
                        a -> {
                            throw new IllegalArgumentException(
                                    "Ascendência " + a.name() + " aponta para a classe "
                                            + a.characterClass().name() + ", que não está no catálogo");
                        });
        for (SkillGem gem : skillGems.values()) {
            for (String weapon : gem.weaponTypes()) {
                if (!weaponTypes.containsKey(CatalogNames.normalize(weapon))) {
                    throw new IllegalArgumentException(
                            "Gema " + gem.name() + " exige o tipo de arma " + weapon + ", que não está no catálogo");
                }
            }
        }
    }

    public static Builder builder(CatalogVersion version) {
        return new Builder(version);
    }

    public CatalogVersion version() {
        return version;
    }

    public Optional<CharacterClass> findClass(String name) {
        return find(classes, name);
    }

    public Optional<Ascendancy> findAscendancy(String name) {
        return find(ascendancies, name);
    }

    public Optional<WeaponType> findWeaponType(String name) {
        return find(weaponTypes, name);
    }

    public Optional<SkillGem> findSkillGem(String name) {
        return find(skillGems, name);
    }

    public Optional<SupportGem> findSupportGem(String name) {
        return find(supportGems, name);
    }

    public Optional<PassiveNotable> findNotable(String name) {
        return find(notables, name);
    }

    public Optional<Playstyle> findPlaystyle(String name) {
        return find(playstyles, name);
    }

    public List<Ascendancy> ascendanciesOf(CharacterClass characterClass) {
        return ascendancies.values().stream().filter(a -> a.belongsTo(characterClass)).toList();
    }

    public List<CharacterClass> classes() {
        return List.copyOf(classes.values());
    }

    public List<Ascendancy> ascendancies() {
        return List.copyOf(ascendancies.values());
    }

    public List<WeaponType> weaponTypes() {
        return List.copyOf(weaponTypes.values());
    }

    public List<SkillGem> skillGems() {
        return List.copyOf(skillGems.values());
    }

    public List<SupportGem> supportGems() {
        return List.copyOf(supportGems.values());
    }

    public List<PassiveNotable> notables() {
        return List.copyOf(notables.values());
    }

    public List<Playstyle> playstyles() {
        return List.copyOf(playstyles.values());
    }

    /**
     * Catálogo novo com o item incluído, ou trocado se já houver um do mesmo tipo e nome. As
     * invariantes são conferidas de novo: ascendência sem classe ou gema com arma inexistente é
     * recusada com {@link IllegalArgumentException}.
     */
    public Catalog with(CatalogItem item) {
        Builder b = copyWithout(item.type(), item.name());
        switch (item.type()) {
            case CLASS -> b.characterClass(new CharacterClass(item.name()));
            case ASCENDANCY -> {
                CharacterClass owner = findClass(item.characterClass())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "A classe " + item.characterClass() + " não existe neste catálogo"));
                b.ascendancy(new Ascendancy(item.name(), owner));
            }
            case WEAPON_TYPE -> b.weaponType(new WeaponType(item.name()));
            case SKILL_GEM -> b.skillGem(new SkillGem(item.name(), item.weaponTypes()));
            case SUPPORT_GEM -> b.supportGem(new SupportGem(item.name()));
            case PLAYSTYLE -> b.playstyle(new Playstyle(item.name()));
        }
        return b.build();
    }

    /** Catálogo novo sem o item. Recusa item inexistente e remoção que deixaria algo órfão. */
    public Catalog without(CatalogItem.Type type, String name) {
        if (!contains(type, name)) {
            throw new IllegalArgumentException(name + " não existe neste catálogo");
        }
        return copyWithout(type, name).build();
    }

    private boolean contains(CatalogItem.Type type, String name) {
        return switch (type) {
            case CLASS -> findClass(name).isPresent();
            case ASCENDANCY -> findAscendancy(name).isPresent();
            case WEAPON_TYPE -> findWeaponType(name).isPresent();
            case SKILL_GEM -> findSkillGem(name).isPresent();
            case SUPPORT_GEM -> findSupportGem(name).isPresent();
            case PLAYSTYLE -> findPlaystyle(name).isPresent();
        };
    }

    private Builder copyWithout(CatalogItem.Type type, String name) {
        String key = CatalogNames.normalize(name);
        Builder b = builder(version);
        keep(classes, CatalogItem.Type.CLASS, type, key).forEach(b::characterClass);
        keep(ascendancies, CatalogItem.Type.ASCENDANCY, type, key).forEach(b::ascendancy);
        keep(weaponTypes, CatalogItem.Type.WEAPON_TYPE, type, key).forEach(b::weaponType);
        keep(skillGems, CatalogItem.Type.SKILL_GEM, type, key).forEach(b::skillGem);
        keep(supportGems, CatalogItem.Type.SUPPORT_GEM, type, key).forEach(b::supportGem);
        keep(playstyles, CatalogItem.Type.PLAYSTYLE, type, key).forEach(b::playstyle);
        b.notables(notables.values());
        return b;
    }

    private static <T> List<T> keep(Map<String, T> index, CatalogItem.Type own, CatalogItem.Type removed, String key) {
        return index.entrySet().stream()
                .filter(e -> own != removed || !e.getKey().equals(key))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static <T> Optional<T> find(Map<String, T> index, String name) {
        return Optional.ofNullable(index.get(CatalogNames.normalize(name)));
    }

    private static <T> Map<String, T> index(Collection<T> items, Function<T, String> name, String kind) {
        Map<String, T> index = new LinkedHashMap<>();
        for (T item : items) {
            T previous = index.putIfAbsent(CatalogNames.normalize(name.apply(item)), item);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Nome duplicado para " + kind + ": " + name.apply(previous));
            }
        }
        return Collections.unmodifiableMap(index);
    }

    /** Montagem incremental, usada pelo adaptador de persistência e pelos testes. */
    public static final class Builder {

        private final CatalogVersion version;
        private final List<CharacterClass> classes = new ArrayList<>();
        private final List<Ascendancy> ascendancies = new ArrayList<>();
        private final List<WeaponType> weaponTypes = new ArrayList<>();
        private final List<SkillGem> skillGems = new ArrayList<>();
        private final List<SupportGem> supportGems = new ArrayList<>();
        private final List<PassiveNotable> notables = new ArrayList<>();
        private final List<Playstyle> playstyles = new ArrayList<>();

        private Builder(CatalogVersion version) {
            this.version = version;
        }

        public Builder characterClass(CharacterClass c) {
            classes.add(c);
            return this;
        }

        public Builder ascendancy(Ascendancy a) {
            ascendancies.add(a);
            return this;
        }

        public Builder weaponType(WeaponType w) {
            weaponTypes.add(w);
            return this;
        }

        public Builder skillGem(SkillGem g) {
            skillGems.add(g);
            return this;
        }

        public Builder supportGem(SupportGem g) {
            supportGems.add(g);
            return this;
        }

        public Builder notable(PassiveNotable n) {
            notables.add(n);
            return this;
        }

        public Builder notables(Collection<PassiveNotable> ns) {
            notables.addAll(ns);
            return this;
        }

        public Builder playstyle(Playstyle p) {
            playstyles.add(p);
            return this;
        }

        public Catalog build() {
            return new Catalog(this);
        }
    }
}
