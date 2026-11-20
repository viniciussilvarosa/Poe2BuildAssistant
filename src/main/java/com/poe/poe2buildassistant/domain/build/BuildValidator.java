package com.poe.poe2buildassistant.domain.build;

import com.poe.poe2buildassistant.domain.build.BuildViolation.Type;
import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogNames;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Guarda da regra dos 100%: um draft só pode ser exibido se todo nome de jogo que ele cita existe
 * no catálogo vigente e se ele respeita as restrições do usuário. Devolve TODAS as violações (não
 * para na primeira) para que a regeneração receba o feedback completo.
 *
 * <p>Equipamentos são atributos em texto livre e não são validados. O estilo de jogo orienta a
 * geração, mas o draft não tem campo para ele, então não é conferido aqui.
 */
public final class BuildValidator {

    public static final int MIN_NOTABLES = 3;
    public static final int MAX_NOTABLES = 8;

    public List<BuildViolation> validate(GeneratedBuildDraft draft, Catalog catalog, BuildRequest request) {
        List<BuildViolation> violations = new ArrayList<>();

        Optional<CharacterClass> characterClass =
                required(violations, "class", draft.characterClass(), catalog::findClass);
        Optional<Ascendancy> ascendancy =
                required(violations, "ascendancy", draft.ascendancy(), catalog::findAscendancy);
        required(violations, "weaponType", draft.weaponType(), catalog::findWeaponType);
        required(violations, "mainSkill", draft.mainSkill(), catalog::findSkillGem);
        draft.supports().forEach(s -> known(violations, "supports", s, catalog::findSupportGem));
        List<String> notables = draft.passiveTree().notables();
        notables.forEach(n -> known(violations, "notables", n, catalog::findNotable));

        if (characterClass.isPresent()
                && ascendancy.isPresent()
                && !ascendancy.get().belongsTo(characterClass.get())) {
            violations.add(new BuildViolation(Type.ASCENDANCY_MISMATCH, "ascendancy", draft.ascendancy()));
        }
        if (notables.size() < MIN_NOTABLES || notables.size() > MAX_NOTABLES) {
            violations.add(new BuildViolation(Type.NOTABLE_COUNT, "notables", String.valueOf(notables.size())));
        }

        Constraints constraints = request.constraints();
        respected(violations, "class", constraints.characterClass(), draft.characterClass());
        respected(violations, "ascendancy", constraints.ascendancy(), draft.ascendancy());
        respected(violations, "weaponType", constraints.weaponType(), draft.weaponType());
        respected(violations, "mainSkill", constraints.mainSkill(), draft.mainSkill());

        return List.copyOf(violations);
    }

    private static <T> Optional<T> required(
            List<BuildViolation> violations, String field, String value, Function<String, Optional<T>> lookup) {
        if (value == null || value.isBlank()) {
            violations.add(new BuildViolation(Type.MISSING_FIELD, field, ""));
            return Optional.empty();
        }
        return known(violations, field, value, lookup);
    }

    private static <T> Optional<T> known(
            List<BuildViolation> violations, String field, String value, Function<String, Optional<T>> lookup) {
        Optional<T> found = lookup.apply(value);
        if (found.isEmpty()) {
            violations.add(new BuildViolation(Type.UNKNOWN_ITEM, field, value));
        }
        return found;
    }

    private static void respected(
            List<BuildViolation> violations, String field, Optional<String> constraint, String value) {
        if (constraint.isEmpty() || value == null || value.isBlank()) {
            return; // sem restrição, ou campo ausente (já reportado como MISSING_FIELD)
        }
        if (!CatalogNames.normalize(constraint.get()).equals(CatalogNames.normalize(value))) {
            violations.add(new BuildViolation(Type.CONSTRAINT_IGNORED, field, value));
        }
    }
}
