package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.adapter.out.passivetree.PassiveTreeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PassiveTreeProperties.class)
class PassiveTreeConfig {}
