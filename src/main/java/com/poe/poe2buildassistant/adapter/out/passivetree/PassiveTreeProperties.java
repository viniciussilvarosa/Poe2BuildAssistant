package com.poe.poe2buildassistant.adapter.out.passivetree;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param url JSON do export oficial da árvore de passivas (repositório grindinggear/poe2-skilltree-export)
 */
@ConfigurationProperties("app.passive-tree")
public record PassiveTreeProperties(
        @DefaultValue("https://raw.githubusercontent.com/grindinggear/poe2-skilltree-export/main/data.json") String url) {}
