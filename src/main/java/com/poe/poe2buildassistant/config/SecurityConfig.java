package com.poe.poe2buildassistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
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
 *   <li>{@code /api/**} é público: o login é opcional no produto.
 *   <li>{@code /api/me/**} exige login; {@code /api/admin/**} exige o papel {@code ADMIN}. Sem
 *       login, a API responde 401 (o SPA decide se mostra "Entrar"); nunca redireciona ao Google.
 *   <li>Login só com Google, por {@code /oauth2/authorization/google}. A sessão fica no servidor
 *       (BFF, ADR-001); ao entrar, volta para "/". Logout por {@code POST /logout} com CSRF.
 *   <li>CSRF no modo SPA: o token vai no cookie {@code XSRF-TOKEN} (legível pelo React) e volta no
 *       header {@code X-XSRF-TOKEN}.
 *   <li>O SPA (arquivos do build e as rotas do React Router) é público só para GET. Um GET para
 *       endereço desconhecido recebe o SPA com 404 (a página "não encontrada" do React); qualquer
 *       outro método fora da API é negado.
 *   <li>Sem cache de requisições: acessos anônimos, inclusive negados, não criam sessão HTTP.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    static final String CONTENT_SECURITY_POLICY = String.join(
            "; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/me/**", "/api/me")
                                        .authenticated()
                                        .requestMatchers("/api/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, SpaForwardController.STATIC)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, SpaForwardController.ROUTES)
                                        .permitAll()
                                        // Navegação para um endereço desconhecido: o ApiExceptionHandler
                                        // devolve o SPA com 404 (bug #48). Só GET; arquivos internos não
                                        // estão em classpath:/static e nunca são servidos.
                                        .requestMatchers(HttpMethod.GET, "/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .csrf(csrf -> csrf.spa())
                // Segunda barreira contra XSS: a #13 exibe texto gerado pelo LLM. Fontes, scripts e
                // estilos são todos da própria origem (Vite + @fontsource), então nada mais é
                // permitido (chaos Y1).
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
                // Requisições negadas não são guardadas em sessão: cada 403 anônimo criava uma
                // sessão HTTP (chaos K4). O login Google (#14) redireciona para "/" explicitamente.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .oauth2Login(login -> login
                        .successHandler(afterLogin())
                        .failureHandler((request, response, e) -> {
                            log.atWarn().setMessage("auth.login.failed").addKeyValue("error", e.getClass().getSimpleName()).log();
                            response.sendRedirect("/?login=falhou");
                        }))
                .logout(logout -> logout.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(errors -> errors
                        // Quem não está logado recebe 401 na API e 403 no resto; o login só começa
                        // pelo link "Entrar", nunca por redirecionamento automático.
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(new Http403ForbiddenEntryPoint(), AnyRequestMatcher.INSTANCE));
        return http.build();
    }

    private static SimpleUrlAuthenticationSuccessHandler afterLogin() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler("/");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }
}
