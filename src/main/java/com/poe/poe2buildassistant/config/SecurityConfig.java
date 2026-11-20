package com.poe.poe2buildassistant.config;

import com.poe.poe2buildassistant.adapter.in.web.SpaForwardController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Configuração de segurança base.
 *
 * <ul>
 *   <li>{@code /api/**} é público: o login é opcional no produto (Google entra no card #14).
 *   <li>{@code /api/admin/**} fica negado até o card de administração (#17) exigir {@code ADMIN}.
 *   <li>CSRF no modo SPA: o token vai no cookie {@code XSRF-TOKEN} (legível pelo React) e volta no
 *       header {@code X-XSRF-TOKEN}.
 *   <li>O SPA (arquivos do build e as rotas do React Router) é público só para GET; qualquer
 *       outra rota é negada.
 *   <li>Sem cache de requisições: acessos anônimos, inclusive negados, não criam sessão HTTP.
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
                                        .requestMatchers(HttpMethod.GET, SpaForwardController.STATIC)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, SpaForwardController.ROUTES)
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .csrf(csrf -> csrf.spa())
                // Requisições negadas não são guardadas em sessão: cada 403 anônimo criava uma
                // sessão HTTP (chaos K4). O login Google (#14) redireciona para "/" explicitamente.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()));
        return http.build();
    }
}
