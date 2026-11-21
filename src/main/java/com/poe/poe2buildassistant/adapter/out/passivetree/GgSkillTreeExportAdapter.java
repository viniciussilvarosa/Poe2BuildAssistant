package com.poe.poe2buildassistant.adapter.out.passivetree;

import com.poe.poe2buildassistant.application.port.out.PassiveTreeSourcePort;
import com.poe.poe2buildassistant.domain.catalog.CatalogNames;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.shared.PassiveTreeFormatChanged;
import com.poe.poe2buildassistant.domain.shared.PassiveTreeUnavailable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê o export oficial da GGG ({@code data.json}) e extrai os notables da árvore principal. Baixa
 * sob demanda (ação do admin), nunca a cada geração. O formato pode mudar a cada patch: qualquer
 * surpresa falha alto, nunca vira lista vazia gravada em silêncio.
 */
@Component
class GgSkillTreeExportAdapter implements PassiveTreeSourcePort {

    /** Marcação dos stats: "[Bow|Bow] [Attack|Attacks]" → texto depois da barra; "[Cold]" → "Cold". */
    private static final Pattern MARKUP = Pattern.compile("\\[(?:[^\\]|]*\\|)?([^\\]]*)]");

    private final String url;
    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    GgSkillTreeExportAdapter(PassiveTreeProperties properties) {
        this(properties.url(), RestClient.create());
    }

    GgSkillTreeExportAdapter(String url, RestClient http) {
        this.url = url;
        this.http = http;
    }

    @Override
    public List<PassiveNotable> fetchNotables() {
        return parse(download());
    }

    private String download() {
        try {
            return http.get().uri(url).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw new PassiveTreeUnavailable("HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new PassiveTreeUnavailable(e.getClass().getSimpleName());
        }
    }

    private List<PassiveNotable> parse(String body) {
        JsonNode nodes;
        try {
            nodes = json.readTree(body == null ? "" : body).path("nodes");
        } catch (JacksonException e) {
            throw new PassiveTreeFormatChanged("o conteúdo não é JSON");
        }
        if (!nodes.isObject()) {
            throw new PassiveTreeFormatChanged("a chave \"nodes\" não existe ou não é um objeto");
        }
        List<PassiveNotable> notables = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode node : nodes.values()) {
            // Notables de ascendência não entram: a build recomenda nós da árvore principal.
            if (!node.path("isNotable").asBoolean(false) || node.hasNonNull("ascendancyId")) {
                continue;
            }
            String name = node.path("name").asString("");
            if (name.isBlank() || !seen.add(CatalogNames.normalize(name))) {
                continue;
            }
            notables.add(new PassiveNotable(name, description(node.path("stats"))));
        }
        if (notables.isEmpty()) {
            throw new PassiveTreeFormatChanged("nenhum nó com \"isNotable\" na árvore principal");
        }
        return notables;
    }

    private static String description(JsonNode stats) {
        List<String> lines = new ArrayList<>();
        for (JsonNode stat : stats.values()) {
            lines.add(MARKUP.matcher(stat.asString("")).replaceAll("$1"));
        }
        return String.join("\n", lines);
    }
}
