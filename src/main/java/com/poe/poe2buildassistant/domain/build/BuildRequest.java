package com.poe.poe2buildassistant.domain.build;

import com.poe.poe2buildassistant.domain.catalog.Ascendancy;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CharacterClass;
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
     * Garante que cada restrição existe no catálogo e que a ascendência pertence à classe escolhida.
     *
     * @throws InvalidBuildRequest com mensagem em pt-BR que cita o valor problemático
     */
    public void validateAgainst(Catalog catalog) {
        Optional<CharacterClass> characterClass =
                require(constraints.characterClass(), catalog::findClass, "Classe");
        Optional<Ascendancy> ascendancy =
                require(constraints.ascendancy(), catalog::findAscendancy, "Ascendência");
        require(constraints.weaponType(), catalog::findWeaponType, "Arma");
        require(constraints.mainSkill(), catalog::findSkillGem, "Habilidade principal");
        require(constraints.playstyle(), catalog::findPlaystyle, "Estilo de jogo");

        if (characterClass.isPresent() && ascendancy.isPresent() && !ascendancy.get().belongsTo(characterClass.get())) {
            throw new InvalidBuildRequest(
                    "A ascendência " + ascendancy.get().name() + " não pertence à classe "
                            + characterClass.get().name() + ".");
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
