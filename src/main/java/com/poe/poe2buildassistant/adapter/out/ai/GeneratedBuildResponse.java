package com.poe.poe2buildassistant.adapter.out.ai;

import java.util.List;

/**
 * Formato JSON pedido ao LLM (o schema é gerado a partir deste record). Fica no adaptador para que
 * mudanças de prompt ou de provedor não toquem no domínio.
 */
record GeneratedBuildResponse(
        String summary,
        String synergy,
        List<ChoiceReason> choiceReasons,
        String characterClass,
        String ascendancy,
        String weaponType,
        String mainSkill,
        List<String> supports,
        PassiveTree passiveTree,
        Gear gear,
        List<String> campaignTips) {

    record ChoiceReason(String field, String reason) {}

    record PassiveTree(String direction, List<String> notables) {}

    record Gear(List<String> weapon, List<String> armour, List<String> accessories) {}
}
