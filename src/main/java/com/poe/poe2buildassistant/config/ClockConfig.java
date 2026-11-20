package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.application.port.out.ClockPort;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    ClockPort clockPort() {
        return Instant::now;
    }
}
