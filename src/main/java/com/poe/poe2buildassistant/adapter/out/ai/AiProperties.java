package com.poe.poe2buildassistant.adapter.out.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do adaptador de IA.
 *
 * @param timeout tempo máximo de uma chamada ao LLM; abaixo dos 30 s da meta p90 do produto
 */
@ConfigurationProperties("app.ai")
public record AiProperties(@DefaultValue("60s") Duration timeout) {}
