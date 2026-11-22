package com.poe.poe2buildassistant.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rotas do SPA React: o navegador pode abrir qualquer uma diretamente (link, recarregar a página),
 * e o React Router decide o que mostrar. Lista explícita de propósito: nada fora dela (e nunca
 * {@code /api/**}) cai no index.html.
 */
@Controller
public class SpaForwardController {

    /** Caminhos do SPA; também liberados no SecurityConfig. */
    public static final String[] ROUTES = {"/guia", "/criar", "/gerando", "/builds/*", "/minhas-builds", "/admin", "/admin/**"};

    /** Arquivos estáticos gerados pelo build do Vite. */
    public static final String[] STATIC = {"/", "/index.html", "/assets/**", "/favicon.svg"};

    @GetMapping({"/guia", "/criar", "/gerando", "/builds/{id}", "/minhas-builds", "/admin", "/admin/**"})
    String forward() {
        return "forward:/index.html";
    }
}
