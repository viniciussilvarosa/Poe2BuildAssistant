package com.poe.poe2buildassistant.domain.build;

import java.util.List;
import java.util.Objects;

/**
 * Rascunho de build devolvido pela IA, ainda NÃO validado. É o contrato que o adaptador do LLM
 * preenche; o {@link BuildValidator} decide se ele pode ser exibido.
 *
 * <p>Como é entrada não confiável, o construtor não rejeita nada: listas nulas viram vazias e
 * campos ausentes ficam nulos para o validador apontar. Nomes de jogo em inglês; textos em pt-BR.
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
    public record ChoiceReason(String field, String reason) {}

    /** Direção na árvore de passivas e os notables-chave (3 a 8). */
    public record PassiveDirection(String direction, List<String> notables) {
        public PassiveDirection {
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
        choiceReasons = choiceReasons == null ? List.of() : List.copyOf(choiceReasons);
        supports = copy(supports);
        passiveTree = Objects.requireNonNullElse(passiveTree, new PassiveDirection("", List.of()));
        gear = Objects.requireNonNullElse(gear, new GearPriorities(List.of(), List.of(), List.of()));
        campaignTips = copy(campaignTips);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }
}
