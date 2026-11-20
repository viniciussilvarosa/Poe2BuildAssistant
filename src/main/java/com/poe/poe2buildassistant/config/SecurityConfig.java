package com.poe.poe2buildassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança base.
 *
 * <ul>
 *   <li>{@code /api/**} é público: o login é opcional no produto (Google entra no card #14).
 *   <li>{@code /api/admin/**} fica negado até o card de administração (#17) exigir {@code ADMIN}.
 *   <li>CSRF no modo SPA: o token vai no cookie {@code XSRF-TOKEN} (legível pelo React) e volta no
 *       header {@code X-XSRF-TOKEN}.
 *   <li>Qualquer outra rota é negada; o card #2 libera os recursos do SPA.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/admin/**")
                                        .denyAll()
                                        .requestMatchers("/api/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .csrf(csrf -> csrf.spa());
        return http.build();
    }
}
