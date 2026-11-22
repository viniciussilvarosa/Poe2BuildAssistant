package com.poe.poe2buildassistant.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.errors.ApiException;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.shared.ProviderUnavailable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

/** Caos do PR #90 (180 s por modelo, prazo de 5 min, lista de reservas). Sem chamadas ao Gemini real. */
class GeminiDeadlineChaosTest {

    private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
    private final BuildRequest pedido = new BuildRequest("arqueiro", Constraints.none().withCharacterClass("Ranger"));
    private final CatalogSlice slice = CatalogSlice.of(CatalogFixtures.catalog(), pedido, 50);
    private final List<String> chamadas = new CopyOnWriteArrayList<>();

    private static String valida() throws IOException {
        return new ClassPathResource("fixtures/gemini/build-response-valid.json").getContentAsString(StandardCharsets.UTF_8);
    }

    private static ChatResponse resposta(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private ChatModel modelo(Function<String, ChatResponse> porModelo) {
        return prompt -> {
            String nome = prompt.getOptions() == null || prompt.getOptions().getModel() == null
                    ? "principal"
                    : prompt.getOptions().getModel();
            chamadas.add(nome);
            return porModelo.apply(nome);
        };
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    @DisplayName("D1: binding real de app.ai.*")
    class Binding {

        private AiProperties bind(Map<String, String> props) {
            return new Binder(new MapConfigurationPropertySource(props))
                    .bindOrCreate("app.ai", AiProperties.class);
        }

        @Test
        @DisplayName("sem app.ai.fallback-models: lista vazia, sem NPE, e timeout padrão de 180 s")
        void semPropriedade() {
            AiProperties p = bind(Map.of());

            assertThat(p.activeFallbacks()).isEmpty();
            assertThat(p.timeout()).isEqualTo(Duration.ofSeconds(180));
        }

        @Test
        @DisplayName("lista com espaços e itens vazios vira só os modelos de verdade, na ordem")
        void listaSuja() {
            AiProperties p = bind(Map.of("app.ai.fallback-models", " gemini-3.6-flash , , gemini-3.5-flash "));

            assertThat(p.activeFallbacks()).containsExactly("gemini-3.6-flash", "gemini-3.5-flash");
        }
    }

    @Nested
    @DisplayName("D2: fronteiras do prazo (relógio fixo, determinístico)")
    class Fronteiras {

        private GeminiBuildGeneratorAdapter adapter(ChatModel model) {
            return new GeminiBuildGeneratorAdapter(
                    ChatClient.builder(model), () -> NOW, new AiProperties(Duration.ofSeconds(180), List.of("gemini-3.6-flash")));
        }

        @Test
        @DisplayName("prazo exatamente agora: nenhuma chamada")
        void prazoExatamenteAgora() {
            var adapter = adapter(modelo(nome -> resposta("{}")));

            assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of(), NOW)).isInstanceOf(ProviderUnavailable.class);
            assertThat(chamadas).isEmpty();
        }

        @Test
        @DisplayName("exatamente 2 s restantes: o reserva ainda é chamado")
        void doisSegundosChamaReserva() throws IOException {
            String json = valida();
            var adapter = adapter(modelo(nome -> {
                if (nome.equals("principal")) {
                    throw new RuntimeException(new ApiException(503, "UNAVAILABLE", "high demand"));
                }
                return resposta(json);
            }));

            assertThat(adapter.generate(pedido, slice, List.of(), NOW.plusSeconds(2)).mainSkill()).isEqualTo("Ice Shot");
            assertThat(chamadas).containsExactly("principal", "gemini-3.6-flash");
        }

        @Test
        @DisplayName("1,999 s restantes: o reserva não é chamado")
        void menosDeDoisSegundosNaoChama() {
            var adapter = adapter(modelo(nome -> {
                throw new RuntimeException(new ApiException(503, "UNAVAILABLE", "high demand"));
            }));

            assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of(), NOW.plusMillis(1_999)))
                    .isInstanceOf(ProviderUnavailable.class);
            assertThat(chamadas).containsExactly("principal");
        }
    }

    @Nested
    @DisplayName("D3: os três modelos lentos dentro do prazo")
    class TresLentos {

        @Test
        @DisplayName("a cadeia usa o tempo que sobra e nunca passa do prazo")
        void cadeiaRespeitaOPrazo() {
            // 1 s por modelo, prazo de 4,5 s: cabem as 3 chamadas (sobram 3,5 s e 2,5 s antes dos reservas).
            var adapter = new GeminiBuildGeneratorAdapter(
                    ChatClient.builder(modelo(nome -> {
                        dormir(5_000);
                        return resposta("{}");
                    })),
                    Instant::now,
                    new AiProperties(Duration.ofSeconds(1), List.of("gemini-3.6-flash", "gemini-3.5-flash")));
            Instant prazo = Instant.now().plusMillis(4_500);
            long inicio = System.nanoTime();

            assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of(), prazo)).isInstanceOf(ProviderUnavailable.class);

            assertThat(chamadas).containsExactly("principal", "gemini-3.6-flash", "gemini-3.5-flash");
            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(4_700));
        }
    }
}
