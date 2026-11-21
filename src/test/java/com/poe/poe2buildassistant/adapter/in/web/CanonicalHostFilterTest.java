package com.poe.poe2buildassistant.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Endereço alternativo do Cloud Run redireciona para o oficial, onde o login com Google funciona (#84). */
class CanonicalHostFilterTest {

    private static final String OFICIAL = "https://poe2buildassistant-877547383313.us-central1.run.app";

    private static MockHttpServletRequest request(String method, String host, String path, String query) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setScheme("https");
        r.setServerName(host);
        r.setServerPort(443);
        r.setQueryString(query);
        return r;
    }

    private static MockHttpServletResponse run(String publicUrl, MockHttpServletRequest request, MockFilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CanonicalHostFilter(publicUrl).doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("endereço alternativo: 301 para o mesmo caminho e query no endereço oficial")
    void alternativoRedireciona() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse r = run(OFICIAL,
                request("GET", "poe2buildassistant-qo5it56ita-uc.a.run.app", "/criar", "conceito=arqueiro"), chain);

        assertThat(r.getStatus()).isEqualTo(301);
        assertThat(r.getHeader("Location")).isEqualTo(OFICIAL + "/criar?conceito=arqueiro");
        assertThat(chain.getRequest()).as("a requisição não segue para o app").isNull();
    }

    @Test
    @DisplayName("login iniciado no alternativo vai para o oficial, onde o redirect do Google está cadastrado")
    void loginVaiParaOOficial() throws Exception {
        MockHttpServletResponse r = run(OFICIAL,
                request("GET", "poe2buildassistant-qo5it56ita-uc.a.run.app", "/oauth2/authorization/google", null),
                new MockFilterChain());

        assertThat(r.getHeader("Location")).isEqualTo(OFICIAL + "/oauth2/authorization/google");
    }

    @Test
    @DisplayName("endereço oficial segue direto, sem redirecionar (inclusive com caixa diferente)")
    void oficialSegue() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse r = run(OFICIAL,
                request("GET", "POE2BUILDASSISTANT-877547383313.us-central1.run.app", "/criar", null), chain);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("sem endereço oficial configurado (dev, localhost), nada é redirecionado")
    void semConfiguracaoNaoFazNada() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        run("", request("GET", "localhost", "/criar", null), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("health check não é redirecionado em nenhum endereço")
    void healthNaoRedireciona() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse r = run(OFICIAL,
                request("GET", "poe2buildassistant-qo5it56ita-uc.a.run.app", "/api/health", null), chain);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Host forjado não vira open redirect: o destino é sempre o endereço oficial")
    void hostForjadoNaoViraOpenRedirect() throws Exception {
        MockHttpServletResponse r = run(OFICIAL,
                request("GET", "evil.example", "//evil.example/%2e%2e", null), new MockFilterChain());

        assertThat(r.getHeader("Location")).startsWith(OFICIAL + "/");
    }

    @Test
    @DisplayName("POST no alternativo usa 308, para o navegador repetir o mesmo método")
    void postUsa308() throws Exception {
        MockHttpServletResponse r = run(OFICIAL,
                request("POST", "poe2buildassistant-qo5it56ita-uc.a.run.app", "/logout", null), new MockFilterChain());

        assertThat(r.getStatus()).isEqualTo(308);
        assertThat(r.getHeader("Location")).isEqualTo(OFICIAL + "/logout");
    }
}
