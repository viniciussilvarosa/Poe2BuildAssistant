package com.poe.poe2buildassistant.config;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Registra o evento {@code app.started} com perfis ativos, versão e modelo de IA. Nunca loga segredos. */
@Component
class StartupLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildProperties;

    StartupLogger(Environment environment, ObjectProvider<BuildProperties> buildProperties) {
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        String version =
                buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
        log.atInfo()
                .setMessage("app.started")
                .addKeyValue("profiles", Arrays.toString(environment.getActiveProfiles()))
                .addKeyValue("version", version)
                // Qual modelo do Gemini está em uso: diagnóstico rápido de erros 404/503 do provedor.
                .addKeyValue("aiModel", environment.getProperty("spring.ai.google.genai.chat.options.model", "padrão do Spring AI"))
                .log();
    }
}
