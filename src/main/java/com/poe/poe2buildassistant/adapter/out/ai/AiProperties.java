package com.poe.poe2buildassistant.adapter.out.ai;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do adaptador de IA.
 *
 * @param timeout tempo máximo de uma chamada ao LLM
 * @param fallbackModels modelos reserva, em ordem: quando o principal (ou o reserva anterior) está
 *     sobrecarregado, sem cota ou estoura o timeout, a mesma tentativa passa para o próximo (#88).
 *     Vazio desliga o reserva.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue("45s") Duration timeout, @DefaultValue({}) List<String> fallbackModels) {

    public AiProperties {
        fallbackModels = fallbackModels == null ? List.of() : List.copyOf(fallbackModels);
    }

    /** Reservas de fato configurados: sem itens vazios ou só com espaço. */
    public List<String> activeFallbacks() {
        return fallbackModels.stream().map(String::strip).filter(m -> !m.isEmpty()).toList();
    }
}
