package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.domain.quota.QuotaPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QuotaConfig.QuotaProperties.class)
class QuotaConfig {

    /**
     * Limites diários provisórios: dependem da medição da cota real da camada gratuita do Gemini
     * (questão em aberto do produto).
     */
    @ConfigurationProperties("app.quota")
    record QuotaProperties(@DefaultValue("3") int anonymousDaily, @DefaultValue("10") int authenticatedDaily) {}

    @Bean
    QuotaPolicy quotaPolicy(QuotaProperties properties) {
        return new QuotaPolicy(properties.anonymousDaily(), properties.authenticatedDaily());
    }
}
