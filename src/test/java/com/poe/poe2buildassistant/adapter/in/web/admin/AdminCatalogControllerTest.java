package com.poe.poe2buildassistant.adapter.in.web.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUser;
import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUsers;
import com.poe.poe2buildassistant.application.port.in.ImportPassiveTreeUseCase;
import com.poe.poe2buildassistant.application.port.in.ManageCatalogUseCase;
import com.poe.poe2buildassistant.application.port.in.PublishCatalogVersionUseCase;
import com.poe.poe2buildassistant.config.SecurityConfig;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Segurança real (SecurityConfig) + casos de uso simulados. */
@WebMvcTest(AdminCatalogController.class)
@Import(SecurityConfig.class)
class AdminCatalogControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

    @Autowired private MockMvc mvc;
    @MockitoBean private ManageCatalogUseCase manage;
    @MockitoBean private PublishCatalogVersionUseCase publish;
    @MockitoBean private ImportPassiveTreeUseCase importTree;

    private static SessionUser admin() {
        return SessionUsers.of(ADMIN_ID, "Curador", "ADMIN");
    }

    @Test
    @DisplayName("usuário sem ADMIN recebe 403 em /api/admin/**")
    void userSemAdmin() throws Exception {
        mvc.perform(post("/api/admin/catalog/drafts").with(csrf()).with(oauth2Login().oauth2User(SessionUsers.of(UUID.randomUUID(), "Ana", "USER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patch\":\"1.0\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(manage);
    }

    @Test
    @DisplayName("visitante recebe 401")
    void visitante() throws Exception {
        mvc.perform(post("/api/admin/catalog/drafts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"patch\":\"1.0\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN sem CSRF é recusado")
    void semCsrf() throws Exception {
        mvc.perform(post("/api/admin/catalog/drafts").with(oauth2Login().oauth2User(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patch\":\"1.0\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(manage);
    }

    @Test
    @DisplayName("ADMIN cria rascunho: 201 com id, patch e status; a auditoria recebe o id do admin")
    void criaRascunho() throws Exception {
        when(manage.createDraft("1.0", ADMIN_ID.toString())).thenReturn(new CatalogVersion(9, "1.0", CatalogVersion.Status.DRAFT));

        mvc.perform(post("/api/admin/catalog/drafts").with(csrf()).with(oauth2Login().oauth2User(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patch\":\"1.0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("PUT de gema no rascunho repassa nome e armas")
    void salvaGema() throws Exception {
        mvc.perform(put("/api/admin/catalog/drafts/9/skill-gems").with(csrf()).with(oauth2Login().oauth2User(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nova Gem\",\"weaponTypes\":[\"Bow\"]}"))
                .andExpect(status().isNoContent());

        verify(manage).saveItem(9L, CatalogItem.skillGem("Nova Gem", List.of("Bow")), ADMIN_ID.toString());
    }

    @Test
    @DisplayName("DELETE remove pelo tipo e nome")
    void removeItem() throws Exception {
        mvc.perform(delete("/api/admin/catalog/drafts/9/support-gems/Pierce I").with(csrf()).with(oauth2Login().oauth2User(admin())))
                .andExpect(status().isNoContent());

        verify(manage).removeItem(9L, CatalogItem.Type.SUPPORT_GEM, "Pierce I", ADMIN_ID.toString());
    }

    @Test
    @DisplayName("tipo de item desconhecido na URL responde 404")
    void tipoDesconhecido() throws Exception {
        mvc.perform(put("/api/admin/catalog/drafts/9/notables").with(csrf()).with(oauth2Login().oauth2User(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("editar versão publicada responde 409 em pt-BR")
    void publicada409() throws Exception {
        org.mockito.Mockito.doThrow(new CatalogVersionNotEditable("0.5")).when(manage).saveItem(eq(1L), any(), any());

        mvc.perform(put("/api/admin/catalog/drafts/1/playstyles").with(csrf()).with(oauth2Login().oauth2User(admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Novo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("publicada")));
    }

    @Test
    @DisplayName("POST /api/admin/catalog/drafts/{id}/passive-tree/import dispara o import")
    void importa() throws Exception {
        when(importTree.importInto(9L)).thenReturn(983);

        mvc.perform(post("/api/admin/catalog/drafts/9/passive-tree/import").with(csrf()).with(oauth2Login().oauth2User(admin())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notables").value(983));

        verify(importTree).importInto(9L);
    }

    @Test
    @DisplayName("publicar responde 200 com a versão publicada")
    void publica() throws Exception {
        when(publish.publish(9L, ADMIN_ID.toString())).thenReturn(new CatalogVersion(9, "1.0", CatalogVersion.Status.PUBLISHED));

        mvc.perform(post("/api/admin/catalog/drafts/9/publish").with(csrf()).with(oauth2Login().oauth2User(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }
}
