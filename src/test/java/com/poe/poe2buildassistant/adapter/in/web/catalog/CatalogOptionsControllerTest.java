package com.poe.poe2buildassistant.adapter.in.web.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase;
import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase.CatalogOptions;
import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase.ClassOption;
import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase.SkillGemOption;
import com.poe.poe2buildassistant.config.SecurityConfig;
import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogOptionsController.class)
@Import(SecurityConfig.class)
class CatalogOptionsControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private GetCatalogOptionsUseCase getOptions;

    private static CatalogOptions options() {
        return new CatalogOptions(
                "0.4",
                List.of(new ClassOption("Ranger", List.of("Deadeye", "Pathfinder"))),
                List.of("Bow"),
                List.of(new SkillGemOption("Ice Shot", List.of("Bow"))),
                List.of("À distância"));
    }

    @Test
    @DisplayName("GET /api/catalog/options devolve opções agrupadas com ascendências por classe")
    void devolveOpcoes() throws Exception {
        when(getOptions.get()).thenReturn(options());

        mvc.perform(get("/api/catalog/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patch").value("0.4"))
                .andExpect(jsonPath("$.classes[0].name").value("Ranger"))
                .andExpect(jsonPath("$.classes[0].ascendancies[*].name", hasItem("Deadeye")))
                .andExpect(jsonPath("$.weaponTypes[0].name").value("Bow"))
                .andExpect(jsonPath("$.skillGems[0].name").value("Ice Shot"))
                .andExpect(jsonPath("$.skillGems[0].weaponTypes[0]").value("Bow"))
                .andExpect(jsonPath("$.playstyles[0].name").value("À distância"));
    }

    @Test
    @DisplayName("é público: responde sem login")
    void publico() throws Exception {
        when(getOptions.get()).thenReturn(options());

        mvc.perform(get("/api/catalog/options")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("pode ficar em cache por 5 minutos: o catálogo muda raramente")
    void cache() throws Exception {
        when(getOptions.get()).thenReturn(options());

        mvc.perform(get("/api/catalog/options"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("max-age=300")));
    }

    @Test
    @DisplayName("sem catálogo publicado responde 503 ProblemDetail em pt-BR")
    void semCatalogo() throws Exception {
        when(getOptions.get()).thenThrow(new CatalogNotPublished());

        mvc.perform(get("/api/catalog/options"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", Matchers.containsString("catálogo")))
                // Um 503 em cache prenderia o formulário no erro por 5 minutos depois da publicação.
                .andExpect(header().string("Cache-Control", Matchers.not(Matchers.containsString("max-age=300"))));
    }
}
