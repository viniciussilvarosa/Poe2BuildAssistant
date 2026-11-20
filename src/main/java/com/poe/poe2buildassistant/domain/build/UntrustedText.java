package com.poe.poe2buildassistant.domain.build;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Limpa texto não confiável (conceito do usuário, respostas do LLM): remove caracteres de controle,
 * exceto quebra de linha, retorno de carro e tab. O {@code \u0000}, por exemplo, é recusado pelo
 * PostgreSQL em jsonb e fazia a gravação falhar depois da geração (chaos T4).
 */
final class UntrustedText {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\n\\r\\t]]");

    private UntrustedText() {}

    static String clean(String text) {
        return text == null ? null : CONTROL.matcher(text).replaceAll("");
    }

    static List<String> clean(List<String> texts) {
        return texts == null ? List.of() : texts.stream().filter(Objects::nonNull).map(UntrustedText::clean).toList();
    }
}
