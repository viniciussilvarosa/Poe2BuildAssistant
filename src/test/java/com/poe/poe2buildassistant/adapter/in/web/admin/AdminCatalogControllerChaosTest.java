package com.poe.poe2buildassistant.adapter.in.web.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUsers;
import com.poe.poe2buildassistant.application.port.in.ImportPassiveTreeUseCase;
import com.poe.poe2buildassistant.application.port.in.ManageCatalogUseCase;
import com.poe.poe2buildassistant.application.port.in.PublishCatalogVersionUseCase;
import com.poe.poe2buildassistant.config.SecurityConfig;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Ataques à API de admin (card #17, PR #38), com a segurança real. */
@WebMvcTest(AdminCatalogController.class)
@Import(SecurityConfig.class)
class AdminCatalogControllerChaosTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private ManageCatalogUseCase manage;
    @MockitoBean private PublishCatalogVersionUseCase publish;
    @MockitoBean private ImportPassiveTreeUseCase importTree;

    @Test
    @DisplayName("C6 — corpo malformado no admin responde 400 sem falar do campo 'concept' (que é do formulário de build)")
    void corpoMalformado() throws Exception {
        mvc.perform(put("/api/admin/catalog/drafts/9/playstyles").with(csrf())
                        .with(oauth2Login().oauth2User(SessionUsers.of(UUID.randomUUID(), "Curador", "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", Matchers.not(Matchers.containsString("concept"))));
    }

    @Test
    @DisplayName("C7 — PUT sem nome responde 400 em pt-BR, não 500")
    void semNome() throws Exception {
        mvc.perform(put("/api/admin/catalog/drafts/9/playstyles").with(csrf())
                        .with(oauth2Login().oauth2User(SessionUsers.of(UUID.randomUUID(), "Curador", "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }
}
