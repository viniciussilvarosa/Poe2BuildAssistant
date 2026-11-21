package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.application.service.AccountSettings;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountConfig.AccountProperties.class)
class AccountConfig {

    private static final Logger log = LoggerFactory.getLogger(AccountConfig.class);

    /** @param adminEmails contas Google que recebem ADMIN no login (ADMIN_EMAILS, separados por vírgula) */
    @ConfigurationProperties("app.security")
    record AccountProperties(@DefaultValue List<String> adminEmails) {}

    @Bean
    AccountSettings accountSettings(
            AccountProperties properties,
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId) {
        if (googleClientId.isBlank() || googleClientId.equals(GOOGLE_NOT_CONFIGURED)) {
            // O app sobe sem as credenciais (o login é opcional); só o botão "Entrar" falha.
            log.atWarn().setMessage("auth.google.not_configured").log();
        }
        return new AccountSettings(Set.copyOf(properties.adminEmails()));
    }

    /** Mesmo valor padrão de GOOGLE_CLIENT_ID no application.properties. */
    static final String GOOGLE_NOT_CONFIGURED = "nao-configurado";
}
