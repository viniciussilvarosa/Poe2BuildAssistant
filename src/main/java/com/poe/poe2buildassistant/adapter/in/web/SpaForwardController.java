package com.poe.poe2buildassistant.adapter.in.web;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rotas do SPA React: o navegador pode abrir qualquer uma diretamente (link, recarregar a página),
 * e o React Router decide o que mostrar. Lista explícita de propósito: nada fora dela (e nunca
 * {@code /api/**}) cai no index.html.
 *
 * <p>O index.html é devolvido direto, sem {@code forward:}. No forward, um HEAD (sem corpo) fechava a
 * resposta antes de o Spring Security escrever CSP, X-Frame-Options e no-store (chaos do PR #91).
 */
@Controller
public class SpaForwardController {

    /** Caminhos do SPA; também liberados no SecurityConfig. */
    public static final String[] ROUTES = {"/guia", "/criar", "/gerando", "/builds/*", "/minhas-builds", "/admin", "/admin/**"};

    /** Arquivos estáticos gerados pelo build do Vite. */
    public static final String[] STATIC = {"/", "/index.html", "/assets/**", "/favicon.svg"};

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @GetMapping({"/", "/guia", "/criar", "/gerando", "/builds/{id}", "/minhas-builds", "/admin", "/admin/**"})
    ResponseEntity<Resource> index() {
        if (!INDEX.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)).body(INDEX);
    }
}
