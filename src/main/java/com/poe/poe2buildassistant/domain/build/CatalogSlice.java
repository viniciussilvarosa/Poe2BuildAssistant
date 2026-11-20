package com.poe.poe2buildassistant.domain.build;

import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.SupportGem;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import java.util.List;
import java.util.Optional;

/**
 * Recorte do catálogo enviado ao LLM: só o que é coerente com as restrições do usuário, limitado
 * por tipo para caber na janela e na cota da camada gratuita. A validação continua usando o
 * catálogo inteiro; o recorte só reduz o que a IA vê.
 */
public record CatalogSlice(
        List<CharacterClass> classes,
        List<Ascendancy> ascendancies,
        List<WeaponType> weaponTypes,
        List<SkillGem> skillGems,
        List<SupportGem> supportGems,
        List<PassiveNotable> notables) {

    public CatalogSlice {
        classes = List.copyOf(classes);
        ascendancies = List.copyOf(ascendancies);
        weaponTypes = List.copyOf(weaponTypes);
        skillGems = List.copyOf(skillGems);
        supportGems = List.copyOf(supportGems);
        notables = List.copyOf(notables);
    }

    /**
     * @param maxItemsPerType limite por tipo; precisa comportar o máximo de notables de uma build
     */
    public static CatalogSlice of(Catalog catalog, BuildRequest request, int maxItemsPerType) {
        if (maxItemsPerType < BuildValidator.MAX_NOTABLES) {
            throw new IllegalArgumentException("Limite do recorte menor que " + BuildValidator.MAX_NOTABLES);
        }
        Constraints c = request.constraints();
        Optional<Ascendancy> ascendancy = c.ascendancy().flatMap(catalog::findAscendancy);
        Optional<SkillGem> mainSkill = c.mainSkill().flatMap(catalog::findSkillGem);

        List<CharacterClass> classes =
                c.characterClass().flatMap(catalog::findClass).map(List::of)
                        .or(() -> ascendancy.map(a -> List.of(a.characterClass())))
                        .orElse(catalog.classes());
        List<Ascendancy> ascendancies =
                ascendancy.map(List::of)
                        .orElseGet(() -> classes.stream().flatMap(cl -> catalog.ascendanciesOf(cl).stream()).toList());
        List<WeaponType> weapons =
                c.weaponType().flatMap(catalog::findWeaponType).map(List::of)
                        .orElseGet(() -> catalog.weaponTypes().stream()
                                .filter(w -> mainSkill.map(s -> s.usableWith(w)).orElse(true))
                                .toList());
        List<SkillGem> skills =
                mainSkill.map(List::of)
                        .orElseGet(() -> catalog.skillGems().stream()
                                .filter(s -> weapons.stream().anyMatch(s::usableWith))
                                .toList());

        return new CatalogSlice(
                limit(classes, maxItemsPerType),
                limit(ascendancies, maxItemsPerType),
                limit(weapons, maxItemsPerType),
                limit(skills, maxItemsPerType),
                limit(catalog.supportGems(), maxItemsPerType),
                limit(catalog.notables(), maxItemsPerType));
    }

    private static <T> List<T> limit(List<T> items, int max) {
        return items.size() <= max ? items : items.subList(0, max);
    }
}
