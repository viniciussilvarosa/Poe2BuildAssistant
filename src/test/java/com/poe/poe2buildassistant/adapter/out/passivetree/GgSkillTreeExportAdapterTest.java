package com.poe.poe2buildassistant.adapter.out.passivetree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.shared.PassiveTreeFormatChanged;
import com.poe.poe2buildassistant.domain.shared.PassiveTreeUnavailable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Lê o export oficial da GGG a partir de fixtures: nenhuma chamada de rede de verdade. */
class GgSkillTreeExportAdapterTest {

    private static final String URL = "https://example.test/data.json";

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("fixtures/passivetree/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<PassiveNotable> fetchWith(String body) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new GgSkillTreeExportAdapter(URL, builder.build()).fetchNotables();
    }

    @Test
    @DisplayName("extrai apenas nós notable do JSON do export")
    void soNotables() throws IOException {
        List<PassiveNotable> notables = fetchWith(fixture("export-reduzido.json"));

        assertThat(notables)
                .extracting(PassiveNotable::name)
                .containsExactlyInAnyOrder("Her Final Bite", "Cold Precision", "Swift Flight");
    }

    @Test
    @DisplayName("notables de ascendência ficam de fora: a build recomenda nós da árvore principal")
    void semAscendencia() throws IOException {
        assertThat(fetchWith(fixture("export-reduzido.json"))).extracting(PassiveNotable::name).doesNotContain("Endless Munitions");
    }

    @Test
    @DisplayName("descrição junta os stats e troca a marcação [id|texto] pelo texto exibido no jogo")
    void descricaoLegivel() throws IOException {
        PassiveNotable bite = fetchWith(fixture("export-reduzido.json")).stream()
                .filter(n -> n.name().equals("Her Final Bite"))
                .findFirst()
                .orElseThrow();

        assertThat(bite.description()).isEqualTo("20% increased Physical Damage with Bows\nBow Attacks have Culling Strike");
        assertThat(fetchWith(fixture("export-reduzido.json")))
                .filteredOn(n -> n.name().equals("Cold Precision"))
                .extracting(PassiveNotable::description)
                .containsExactly("25% increased Cold Damage");
    }

    @Test
    @DisplayName("falha com mensagem clara quando o formato muda")
    void formatoMudou() throws IOException {
        String body = fixture("export-sem-nos.json");

        assertThatThrownBy(() -> fetchWith(body)).isInstanceOf(PassiveTreeFormatChanged.class).hasMessageContaining("nodes");
    }

    @Test
    @DisplayName("JSON válido sem nenhum notable também é formato mudado, nunca lista vazia")
    void semNotables() {
        assertThatThrownBy(() -> fetchWith("{\"nodes\": {\"1\": {\"name\": \"Life\", \"stats\": []}}}"))
                .isInstanceOf(PassiveTreeFormatChanged.class);
    }

    @Test
    @DisplayName("resposta que nem é JSON vira formato mudado")
    void naoJson() {
        assertThatThrownBy(() -> fetchWith("<html>rate limited</html>")).isInstanceOf(PassiveTreeFormatChanged.class);
    }

    @Test
    @DisplayName("erro HTTP do GitHub vira fonte indisponível, com o status")
    void erroHttp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> new GgSkillTreeExportAdapter(URL, builder.build()).fetchNotables())
                .isInstanceOf(PassiveTreeUnavailable.class)
                .hasMessageContaining("500");
    }
}
