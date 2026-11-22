package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.errors.ApiException;
import com.poe.poe2buildassistant.adapter.in.web.build.GenerationStreamTimeout;
import com.poe.poe2buildassistant.adapter.out.ai.GeminiAdapterProbe;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.shared.GenerationFailed;
import com.poe.poe2buildassistant.domain.shared.ProviderUnavailable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

/**
 * Caos do PR #89 (modelo reserva e limite de 5 min). Tudo simulado: nenhuma chamada ao Gemini real.
 * O adaptador é package-private; os ataques passam pelo mesmo pacote via {@link GeminiAdapterProbe}.
 */
class GeminiFallbackChaosTest {

    static final Instant NOW = Instant.parse("2026-11-22T14:00:00Z");
    static final String RESERVA = "gemini-3.5-flash";
    static final String PRINCIPAL = "(padrão)";

    final BuildRequest pedido = new BuildRequest("arqueiro mágico", Constraints.none().withCharacterClass("Ranger"));
    final CatalogSlice slice = CatalogSlice.of(CatalogFixtures.catalog(), pedido, 50);
    final List<String> chamadas = new CopyOnWriteArrayList<>();

    static String valida() throws IOException {
        return new ClassPathResource("fixtures/gemini/build-response-valid.json").getContentAsString(StandardCharsets.UTF_8);
    }

    static ChatResponse resposta(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    static RuntimeException api(int code, String status, String message) {
        return new RuntimeException(new ApiException(code, status, message));
    }

    static String modeloPedido(Prompt prompt) {
        String nome = prompt.getOptions() == null ? null : prompt.getOptions().getModel();
        return nome == null || nome.isBlank() ? PRINCIPAL : nome;
    }

    ChatModel modelo(Function<String, ChatResponse> porModelo) {
        return prompt -> {
            String nome = modeloPedido(prompt);
            chamadas.add(nome);
            return porModelo.apply(nome);
        };
    }

    static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Nested
    @DisplayName("C1: cota do reserva não pode bloquear o principal")
    class CotaDoReserva {

        @Test
        @DisplayName("3.6 com 503 e 3.5 sem cota (429): quando o 3.6 volta, a geração seguinte usa o 3.6 em vez de ficar bloqueada")
        void cotaDoReservaNaoBloqueiaPrincipal() throws Exception {
            String json = valida();
            boolean[] principalVoltou = {false};
            var adapter = GeminiAdapterProbe.adapter(modelo(nome -> {
                if (nome.equals(RESERVA)) {
                    throw api(429, "RESOURCE_EXHAUSTED", "{\"retryDelay\": \"36000s\"}");
                }
                if (!principalVoltou[0]) {
                    throw api(503, "UNAVAILABLE", "high demand");
                }
                return resposta(json);
            }), RESERVA, Duration.ofSeconds(5), () -> NOW);

            assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);

            principalVoltou[0] = true;
            chamadas.clear();

            assertThat(adapter.generate(pedido, slice, List.of()).mainSkill())
                    .as("o 3.6 já responde; a cota esgotada era só do 3.5")
                    .isEqualTo("Ice Shot");
            assertThat(chamadas).containsExactly(PRINCIPAL);
        }
    }

    @Nested
    @DisplayName("C2: resposta ruim do reserva")
    class RespostaRuimDoReserva {

        @Test
        @DisplayName("reserva com JSON malformado é falha de geração (conta como tentativa), não 'IA indisponível'")
        void reservaMalformado() {
            var adapter = GeminiAdapterProbe.adapter(modelo(nome -> {
                if (nome.equals(PRINCIPAL)) {
                    throw api(503, "UNAVAILABLE", "high demand");
                }
                return resposta("{\"summary\": \"corta aqui");
            }), RESERVA, Duration.ofSeconds(5), () -> NOW);

            assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(GenerationFailed.class);
            assertThat(chamadas).containsExactly(PRINCIPAL, RESERVA);
        }
    }

    @Nested
    @DisplayName("C3: os dois modelos lentos")
    class DoisLentos {

        @Test
        @DisplayName("principal e reserva estourando: cada um tem o próprio timeout e a espera não passa de 2x")
        void esperaLimitada() {
            var adapter = GeminiAdapterProbe.adapter(modelo(nome -> {
                dormir(3_000);
                return resposta("{}");
            }), RESERVA, Duration.ofMillis(300), () -> NOW);
            long inicio = System.nanoTime();

            assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);

            long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            assertThat(ms).as("2 x 300 ms + folga").isLessThan(1_500);
            assertThat(chamadas).containsExactly(PRINCIPAL, RESERVA);
        }
    }

    @Nested
    @DisplayName("C4: requisição interrompida")
    class Interrompida {

        @Test
        @DisplayName("thread interrompida (usuário saiu) não aciona o reserva nem gasta outra chamada")
        void interrupcaoNaoUsaReserva() throws Exception {
            var adapter = GeminiAdapterProbe.adapter(modelo(nome -> {
                dormir(2_000);
                return resposta("{}");
            }), RESERVA, Duration.ofSeconds(5), () -> NOW);

            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);
            } finally {
                Thread.interrupted(); // limpa a flag para não vazar para outros testes
            }
            dormir(100);
            assertThat(chamadas).doesNotContain(RESERVA);
        }
    }

    @Nested
    @DisplayName("C5: limites do tempo máximo da geração")
    class LimitesDoTempo {

        @Test
        @DisplayName("limite exatamente no mínimo (2x a folga) é aceito, com prazo igual à folga")
        void noMinimo() {
            assertThat(GenerationStreamTimeout.of(Duration.ofSeconds(60)).attemptsBudget()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("um segundo abaixo do mínimo é recusado")
        void umSegundoAbaixo() {
            assertThatThrownBy(() -> GenerationStreamTimeout.of(Duration.ofSeconds(59))).isInstanceOf(IllegalStateException.class);
        }

        @ParameterizedTest(name = "reserva {0} conta como sem reserva")
        @ValueSource(strings = {"", "   ", "\t"})
        void reservaEmBrancoDesliga(String reserva) {
            var adapter = GeminiAdapterProbe.adapter(modelo(nome -> {
                throw api(503, "UNAVAILABLE", "high demand");
            }), reserva, Duration.ofSeconds(5), () -> NOW);

            assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);
            assertThat(chamadas).containsExactly(PRINCIPAL);
        }
    }

    @Nested
    @DisplayName("C6: outros 5xx")
    class Outros5xx {

        @ParameterizedTest(name = "HTTP {0} no principal aciona o reserva")
        @ValueSource(ints = {500, 502, 504})
        void outrosErrosDeServidor(int code) throws Exception {
            String json = valida();
            var adapter = GeminiAdapterProbe.adapter(modelo(nome -> {
                if (nome.equals(PRINCIPAL)) {
                    throw api(code, "INTERNAL", "erro do servidor");
                }
                return resposta(json);
            }), RESERVA, Duration.ofSeconds(5), () -> NOW);

            assertThat(adapter.generate(pedido, slice, List.of()).mainSkill()).isEqualTo("Ice Shot");
            assertThat(chamadas).containsExactly(PRINCIPAL, RESERVA);
        }
    }
}
