package com.poe.poe2buildassistant.adapter.out.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do adaptador de IA.
 *
 * @param timeout tempo máximo de uma chamada ao LLM
 * @param fallbackModel modelo tentado na mesma geração quando o principal está sobrecarregado (5xx)
 *     ou estoura o timeout; vazio desliga o reserva (#88)
 */
@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue("60s") Duration timeout, @DefaultValue("") String fallbackModel) {}
