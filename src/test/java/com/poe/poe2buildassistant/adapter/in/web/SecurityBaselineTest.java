package com.poe.poe2buildassistant.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class SecurityBaselineTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("endpoint público /api/health responde sem login")
    void healthEPublico() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("POST sem token CSRF é rejeitado")
    void postSemCsrfERejeitado() throws Exception {
        mvc.perform(post("/api/health")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST com token CSRF passa pelo filtro (chega ao dispatcher: 405, não 403)")
    void postComCsrfPassaPeloFiltro() throws Exception {
        mvc.perform(post("/api/health").with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("/api/admin/** fica negado até o card de administração")
    void adminNegado() throws Exception {
        mvc.perform(get("/api/admin/catalog")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/health emite o cookie XSRF-TOKEN legível pelo SPA")
    void emiteCookieCsrf() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
