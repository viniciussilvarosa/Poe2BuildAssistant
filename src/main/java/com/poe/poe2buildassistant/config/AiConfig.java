package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.adapter.out.ai.AiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {}
