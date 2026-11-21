package com.poe.poe2buildassistant.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Leva quem chega por outro endereço para o endereço oficial do app ({@code app.public-url}). O Cloud
 * Run publica o serviço em mais de um endereço, e o login com Google só funciona no que tem o
 * redirect cadastrado no cliente OAuth (#84).
 *
 * <p>O destino é montado só com o endereço configurado mais o caminho e a query da requisição, nunca
 * com o host recebido: um {@code Host} forjado não vira open redirect. Sem {@code app.public-url}
 * (dev, localhost), não faz nada. O health check nunca é redirecionado.
 *
 * <p>Roda depois do filtro de forwarded headers do Spring, então o host comparado já é o que o
 * proxy repassou.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CanonicalHostFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CanonicalHostFilter.class);
    private static final String HEALTH = "/api/health";

    private final String baseUrl;
    private final String host;

    CanonicalHostFilter(@Value("${app.public-url:}") String publicUrl) {
        String url = publicUrl == null ? "" : publicUrl.strip();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String parsedHost = url.isEmpty() ? null : URI.create(url).getHost();
        if (!url.isEmpty() && parsedHost == null) {
            throw new IllegalStateException("app.public-url precisa ser uma URL absoluta, ex.: https://exemplo.app");
        }
        this.host = parsedHost == null ? "" : parsedHost.toLowerCase(Locale.ROOT);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return host.isEmpty()
                || HEALTH.equals(request.getRequestURI())
                || host.equals(String.valueOf(request.getServerName()).toLowerCase(Locale.ROOT));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String query = request.getQueryString();
        String target = baseUrl + request.getRequestURI() + (query == null ? "" : "?" + query);
        boolean safeMethod = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        log.atInfo()
                .setMessage("http.redirect.canonical_host")
                .addKeyValue("fromHost", request.getServerName())
                .log();
        // 308 mantém o método e o corpo (ex.: POST /logout); 301 para navegação comum.
        response.setStatus(safeMethod ? HttpServletResponse.SC_MOVED_PERMANENTLY : 308);
        response.setHeader(HttpHeaders.LOCATION, target);
    }
}
