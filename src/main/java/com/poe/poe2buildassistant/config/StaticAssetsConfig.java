package com.poe.poe2buildassistant.config;

import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Os arquivos de /assets têm hash no nome (conteúdo novo = nome novo), então podem ficar em cache
 * por um ano. O index.html continua sem cache, para sempre apontar para os assets atuais
 * (chaos Y2). O Spring Security não sobrescreve um Cache-Control já definido.
 */
@Configuration(proxyBeanMethods = false)
class StaticAssetsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
