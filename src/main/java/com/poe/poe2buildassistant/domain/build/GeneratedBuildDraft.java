package com.poe.poe2buildassistant.domain.build;

import java.util.List;
import java.util.Objects;

/**
 * Rascunho de build devolvido pela IA, ainda NÃO validado. É o contrato que o adaptador do LLM
 * preenche; o {@link BuildValidator} decide se ele pode ser exibido.
 *
 * <p>Como é entrada não confiável, o construtor não rejeita nada: listas nulas viram vazias,
 * campos ausentes ficam nulos para o validador apontar e caracteres de controle são removidos. Nomes de jogo em inglês; textos em pt-BR.
 */
public record GeneratedBuildDraft(
        String summary,
        String synergy,
        List<ChoiceReason> choiceReasons,
        String characterClass,
        String ascendancy,
        String weaponType,
        String mainSkill,
        List<String> supports,
        PassiveDirection passiveTree,
        GearPriorities gear,
        List<String> campaignTips) {

    /** Por que a IA escolheu um campo que o usuário deixou em "Deixar a IA escolher". */
    public record ChoiceReason(String field, String reason) {
        public ChoiceReason {
            field = UntrustedText.clean(field);
            reason = UntrustedText.clean(reason);
        }
    }

    /** Direção na árvore de passivas e os notables-chave (3 a 8). */
    public record PassiveDirection(String direction, List<String> notables) {
        public PassiveDirection {
            direction = UntrustedText.clean(direction);
            notables = copy(notables);
        }
    }

    /** Atributos prioritários por slot. Texto livre: não é validado contra o catálogo. */
    public record GearPriorities(List<String> weapon, List<String> armour, List<String> accessories) {
        public GearPriorities {
            weapon = copy(weapon);
            armour = copy(armour);
            accessories = copy(accessories);
        }
    }

    public GeneratedBuildDraft {
        summary = UntrustedText.clean(summary);
        synergy = UntrustedText.clean(synergy);
        characterClass = UntrustedText.clean(characterClass);
        ascendancy = UntrustedText.clean(ascendancy);
        weaponType = UntrustedText.clean(weaponType);
        mainSkill = UntrustedText.clean(mainSkill);
        choiceReasons =
                choiceReasons == null ? List.of() : choiceReasons.stream().filter(Objects::nonNull).toList();
        supports = copy(supports);
        passiveTree = Objects.requireNonNullElse(passiveTree, new PassiveDirection("", List.of()));
        gear = Objects.requireNonNullElse(gear, new GearPriorities(List.of(), List.of(), List.of()));
        campaignTips = copy(campaignTips);
    }

    private static List<String> copy(List<String> values) {
        return UntrustedText.clean(values);
    }
}
