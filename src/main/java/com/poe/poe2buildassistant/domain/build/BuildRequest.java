package com.poe.poe2buildassistant.domain.build;

import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.WeaponType;
import com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Pedido do usuário: conceito em texto livre (obrigatório) e restrições opcionais. */
public record BuildRequest(String concept, Constraints constraints) {

    public BuildRequest {
        if (concept == null || concept.isBlank()) {
            throw new InvalidBuildRequest("O conceito do personagem é obrigatório.");
        }
        concept = concept.strip();
        constraints = Objects.requireNonNullElse(constraints, Constraints.none());
    }

    /**
     * Garante que cada restrição existe no catálogo, que a ascendência pertence à classe escolhida e
     * que a habilidade escolhida pode ser usada com a arma escolhida.
     *
     * @throws InvalidBuildRequest com mensagem em pt-BR que cita o valor problemático
     */
    public void validateAgainst(Catalog catalog) {
        Optional<CharacterClass> characterClass =
                require(constraints.characterClass(), catalog::findClass, "Classe");
        Optional<Ascendancy> ascendancy =
                require(constraints.ascendancy(), catalog::findAscendancy, "Ascendência");
        Optional<WeaponType> weaponType = require(constraints.weaponType(), catalog::findWeaponType, "Arma");
        Optional<SkillGem> mainSkill =
                require(constraints.mainSkill(), catalog::findSkillGem, "Habilidade principal");
        require(constraints.playstyle(), catalog::findPlaystyle, "Estilo de jogo");

        if (characterClass.isPresent() && ascendancy.isPresent() && !ascendancy.get().belongsTo(characterClass.get())) {
            throw new InvalidBuildRequest(
                    "A ascendência " + ascendancy.get().name() + " não pertence à classe "
                            + characterClass.get().name() + ".");
        }
        if (mainSkill.isPresent() && weaponType.isPresent() && !mainSkill.get().usableWith(weaponType.get())) {
            throw new InvalidBuildRequest(
                    "A habilidade " + mainSkill.get().name() + " não pode ser usada com " + weaponType.get().name()
                            + ".");
        }
    }

    private static <T> Optional<T> require(Optional<String> value, Function<String, Optional<T>> lookup, String label) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Optional<T> found = lookup.apply(value.get());
        if (found.isEmpty()) {
            throw new InvalidBuildRequest(label + " \"" + value.get() + "\" não existe no catálogo atual.");
        }
        return found;
    }
}
