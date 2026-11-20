package com.poe.poe2buildassistant.domain.build;

/**
 * Motivo pelo qual um draft não pode ser exibido. Volta ao LLM como feedback na regeneração.
 *
 * @param field campo do draft ({@code class}, {@code ascendancy}, {@code weaponType}, {@code
 *     mainSkill}, {@code supports}, {@code notables}, {@code summary}, {@code synergy}, {@code
 *     passiveTree})
 * @param itemName valor problemático como veio do draft (vazio quando o campo está ausente)
 */
public record BuildViolation(Type type, String field, String itemName) {

    public enum Type {
        /** Nome que não existe no catálogo vigente. */
        UNKNOWN_ITEM,
        /** Campo obrigatório ausente ou em branco. */
        MISSING_FIELD,
        /** O draft contradiz uma restrição escolhida pelo usuário. */
        CONSTRAINT_IGNORED,
        /** A ascendência não pertence à classe do draft. */
        ASCENDANCY_MISMATCH,
        /** Quantidade de notables distintos fora do intervalo 3–8. */
        NOTABLE_COUNT,
        /** O mesmo suporte ou notable aparece mais de uma vez. */
        DUPLICATE_ITEM,
        /** Quantidade de suportes fora do intervalo 1–5. */
        SUPPORT_COUNT
    }
}
