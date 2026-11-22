package com.poe.poe2buildassistant.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.errors.ApiException;
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
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

/** Modelo reserva quando o principal está sobrecarregado (#88). */
class GeminiFallbackTest {

    private static final Instant NOW = Instant.parse("2026-11-22T14:00:00Z");
    private static final String RESERVA = "gemini-3.5-flash";
    private static final String PRINCIPAL = "(padrão)";

    private final BuildRequest pedido = new BuildRequest("arqueiro mágico", Constraints.none().withCharacterClass("Ranger"));
    private final CatalogSlice slice = CatalogSlice.of(CatalogFixtures.catalog(), pedido, 50);
    /** Modelo pedido em cada chamada, na ordem: as opções do prompt, ou "(padrão)" sem modelo explícito. */
    private final List<String> chamadas = new CopyOnWriteArrayList<>();

    private static String valida() throws IOException {
        return new ClassPathResource("fixtures/gemini/build-response-valid.json").getContentAsString(StandardCharsets.UTF_8);
    }

    private static ChatResponse resposta(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private static RuntimeException api(int code, String status, String message) {
        return new RuntimeException(new ApiException(code, status, message));
    }

    /** Modelo de mentira: decide a resposta pelo nome do modelo que a chamada pediu. */
    private ChatModel modelo(Function<String, ChatResponse> porModelo) {
        return prompt -> {
            String nome = modeloPedido(prompt);
            chamadas.add(nome);
            return porModelo.apply(nome);
        };
    }

    private static String modeloPedido(Prompt prompt) {
        String nome = prompt.getOptions() == null ? null : prompt.getOptions().getModel();
        return nome == null || nome.isBlank() ? PRINCIPAL : nome;
    }

    private GeminiBuildGeneratorAdapter adapter(ChatModel model, String reserva, Duration timeout) {
        return new GeminiBuildGeneratorAdapter(ChatClient.builder(model), () -> NOW, new AiProperties(timeout, List.of(reserva)));
    }

    private GeminiBuildGeneratorAdapter adapter(ChatModel model) {
        return adapter(model, RESERVA, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("503 no principal: a mesma tentativa chama o reserva e a build sai dele")
    void sobrecargaUsaReserva() throws IOException {
        String json = valida();
        var adapter = adapter(modelo(nome -> {
            if (nome.equals(PRINCIPAL)) {
                throw api(503, "UNAVAILABLE", "This model is currently experiencing high demand.");
            }
            return resposta(json);
        }));

        assertThat(adapter.generate(pedido, slice, List.of()).mainSkill()).isEqualTo("Ice Shot");
        assertThat(chamadas).containsExactly(PRINCIPAL, RESERVA);
    }

    @Test
    @DisplayName("timeout no principal: a mesma tentativa chama o reserva")
    void timeoutUsaReserva() throws IOException {
        String json = valida();
        var adapter = adapter(modelo(nome -> {
            if (nome.equals(PRINCIPAL)) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return resposta(json);
        }), RESERVA, Duration.ofMillis(300));

        assertThat(adapter.generate(pedido, slice, List.of()).mainSkill()).isEqualTo("Ice Shot");
        assertThat(chamadas).containsExactly(PRINCIPAL, RESERVA);
    }

    @Test
    @DisplayName("429 de cota não aciona o reserva: vai para o disjuntor, sem gastar a cota do outro modelo")
    void cotaNaoUsaReserva() {
        var adapter = adapter(modelo(nome -> {
            throw api(429, "RESOURCE_EXHAUSTED", "{\"retryDelay\": \"38s\"}");
        }));

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);
        assertThat(chamadas).containsExactly(PRINCIPAL);
    }

    @Test
    @DisplayName("erro de configuração (404) não aciona o reserva")
    void configuracaoNaoUsaReserva() {
        var adapter = adapter(modelo(nome -> {
            throw api(404, "NOT_FOUND", "models/x is not found");
        }));

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);
        assertThat(chamadas).containsExactly(PRINCIPAL);
    }

    @Test
    @DisplayName("resposta malformada não aciona o reserva: é falha de geração, não do provedor")
    void malformadaNaoUsaReserva() {
        var adapter = adapter(modelo(nome -> resposta("{\"summary\": \"corta aqui")));

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(GenerationFailed.class);
        assertThat(chamadas).containsExactly(PRINCIPAL);
    }

    @Test
    @DisplayName("reserva também indisponível: IA indisponível, como hoje")
    void reservaTambemFalha() {
        var adapter = adapter(modelo(nome -> {
            throw api(503, "UNAVAILABLE", "high demand");
        }));

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);
        assertThat(chamadas).containsExactly(PRINCIPAL, RESERVA);
    }

    @Test
    @DisplayName("reserva sem cota (429): o erro não leva o retryAt do reserva, e na próxima geração o reserva nem é chamado")
    void reservaBloqueadoNaoEhChamado() {
        var adapter = adapter(modelo(nome -> {
            if (nome.equals(RESERVA)) {
                throw api(429, "RESOURCE_EXHAUSTED", "{\"retryDelay\": \"3600s\"}");
            }
            throw api(503, "UNAVAILABLE", "high demand");
        }));

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of()))
                .isInstanceOfSatisfying(ProviderUnavailable.class, e -> assertThat(e.retryAt())
                        .as("o 3.6 está só sobrecarregado: o horário do 429 do 3.5 não vale para ele")
                        .isEmpty());
        chamadas.clear();

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);
        assertThat(chamadas).as("o principal é tentado; o reserva bloqueado, não").containsExactly(PRINCIPAL);
    }

    @Test
    @DisplayName("cadeia de reservas: 3.8 e 3.6 sobrecarregados, a build sai do 3.5, na ordem configurada")
    void cadeiaDeReservas() throws IOException {
        String json = valida();
        var adapter = new GeminiBuildGeneratorAdapter(ChatClient.builder(modelo(nome -> {
            if (!nome.equals("gemini-3.5-flash")) {
                throw api(503, "UNAVAILABLE", "high demand");
            }
            return resposta(json);
        })), () -> NOW, new AiProperties(Duration.ofSeconds(5), List.of("gemini-3.6-flash", " ", "gemini-3.5-flash")));

        assertThat(adapter.generate(pedido, slice, List.of()).mainSkill()).isEqualTo("Ice Shot");
        assertThat(chamadas).containsExactly(PRINCIPAL, "gemini-3.6-flash", "gemini-3.5-flash");
    }

    @Test
    @DisplayName("reserva sem cota é pulado: segue para o próximo, e na geração seguinte nem é chamado")
    void reservaSemCotaEhPulado() throws IOException {
        String json = valida();
        var adapter = new GeminiBuildGeneratorAdapter(ChatClient.builder(modelo(nome -> {
            if (nome.equals("gemini-3.6-flash")) {
                throw api(429, "RESOURCE_EXHAUSTED", "{\"retryDelay\": \"3600s\"}");
            }
            if (nome.equals(PRINCIPAL)) {
                throw api(503, "UNAVAILABLE", "high demand");
            }
            return resposta(json);
        })), () -> NOW, new AiProperties(Duration.ofSeconds(5), List.of("gemini-3.6-flash", "gemini-3.5-flash")));

        assertThat(adapter.generate(pedido, slice, List.of()).mainSkill()).isEqualTo("Ice Shot");
        assertThat(chamadas).containsExactly(PRINCIPAL, "gemini-3.6-flash", "gemini-3.5-flash");
        chamadas.clear();

        assertThat(adapter.generate(pedido, slice, List.of()).mainSkill()).isEqualTo("Ice Shot");
        assertThat(chamadas).as("3.6 bloqueado pelo próprio 429").containsExactly(PRINCIPAL, "gemini-3.5-flash");
    }

    @Test
    @DisplayName("sem modelo reserva configurado, o 503 termina na primeira chamada")
    void semReserva() {
        var adapter = adapter(modelo(nome -> {
            throw api(503, "UNAVAILABLE", "high demand");
        }), "", Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of())).isInstanceOf(ProviderUnavailable.class);
        assertThat(chamadas).containsExactly(PRINCIPAL);
    }
}
