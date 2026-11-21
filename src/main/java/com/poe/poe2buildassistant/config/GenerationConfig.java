package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.application.service.GenerationSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GenerationConfig.GenerationProperties.class)
class GenerationConfig {

    /**
     * @param maxAttempts chamadas ao LLM por pedido (questão em aberto do produto; 2 = 1 regeneração)
     * @param sliceMaxItems limite por tipo de item no recorte enviado ao LLM
     */
    @ConfigurationProperties("app.generation")
    record GenerationProperties(@DefaultValue("2") int maxAttempts, @DefaultValue("1000") int sliceMaxItems) {}

    @Bean
    GenerationSettings generationSettings(GenerationProperties properties) {
        return new GenerationSettings(properties.maxAttempts(), properties.sliceMaxItems());
    }
}
