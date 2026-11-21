package com.poe.poe2buildassistant.adapter.out.passivetree;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param url JSON do export oficial da árvore de passivas (repositório grindinggear/poe2-skilltree-export)
 * @param connectTimeout tempo para abrir a conexão com o GitHub
 * @param readTimeout tempo máximo de espera pela resposta; sem ele, um servidor mudo prendia o import
 *     para sempre (chaos N4)
 */
@ConfigurationProperties("app.passive-tree")
public record PassiveTreeProperties(
        @DefaultValue("https://raw.githubusercontent.com/grindinggear/poe2-skilltree-export/main/data.json") String url,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout) {

    /** Só a URL muda; os timeouts ficam nos mesmos valores padrão das anotações acima. */
    public static PassiveTreeProperties withDefaultTimeouts(String url) {
        return new PassiveTreeProperties(url, Duration.ofSeconds(10), Duration.ofSeconds(20));
    }
}
