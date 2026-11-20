package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.adapter.in.web.RequesterResolver;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    /**
     * Salt do hash de IP. Sem {@code QUOTA_IP_SALT}, gera um aleatório por processo: o IP continua
     * protegido, mas as cotas de visitantes recomeçam a cada reinício (aceitável em dev).
     */
    @Bean
    RequesterResolver requesterResolver(@Value("${app.quota.ip-salt:}") String salt) {
        if (salt.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            salt = Base64.getEncoder().encodeToString(random);
            log.atWarn().setMessage("quota.ip_salt.ephemeral").log();
        }
        return new RequesterResolver(salt);
    }

    /** Uma virtual thread por geração: a espera pelo LLM não ocupa threads do servidor. */
    @Bean(name = "buildGenerationExecutor", destroyMethod = "close")
    ExecutorService buildGenerationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
