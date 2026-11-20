package com.poe.poe2buildassistant.domain.build;

import java.util.Optional;

/**
 * Seletores opcionais do formulário. Vazio = "Deixar a IA escolher"; preenchido = restrição que a
 * build gerada é obrigada a respeitar. Valores em branco são tratados como vazios.
 */
public record Constraints(
        Optional<String> characterClass,
        Optional<String> ascendancy,
        Optional<String> weaponType,
        Optional<String> mainSkill,
        Optional<String> playstyle) {

    private static final Constraints NONE =
            new Constraints(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public Constraints {
        characterClass = clean(characterClass);
        ascendancy = clean(ascendancy);
        weaponType = clean(weaponType);
        mainSkill = clean(mainSkill);
        playstyle = clean(playstyle);
    }

    public static Constraints none() {
        return NONE;
    }

    public Constraints withCharacterClass(String value) {
        return new Constraints(Optional.ofNullable(value), ascendancy, weaponType, mainSkill, playstyle);
    }

    public Constraints withAscendancy(String value) {
        return new Constraints(characterClass, Optional.ofNullable(value), weaponType, mainSkill, playstyle);
    }

    public Constraints withWeaponType(String value) {
        return new Constraints(characterClass, ascendancy, Optional.ofNullable(value), mainSkill, playstyle);
    }

    public Constraints withMainSkill(String value) {
        return new Constraints(characterClass, ascendancy, weaponType, Optional.ofNullable(value), playstyle);
    }

    public Constraints withPlaystyle(String value) {
        return new Constraints(characterClass, ascendancy, weaponType, mainSkill, Optional.ofNullable(value));
    }

    private static Optional<String> clean(Optional<String> value) {
        return value == null ? Optional.empty() : value.map(String::strip).filter(v -> !v.isEmpty());
    }
}
