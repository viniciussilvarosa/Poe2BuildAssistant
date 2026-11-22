package com.poe.poe2buildassistant.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ClassPathResource;

/** Cada modelo tem até app.ai.timeout, mas a geração inteira respeita o prazo (5 min). */
class GeminiDeadlineTest {

    private final BuildRequest pedido = new BuildRequest("arqueiro", Constraints.none().withCharacterClass("Ranger"));
    private final CatalogSlice slice = CatalogSlice.of(CatalogFixtures.catalog(), pedido, 50);
    private final List<String> chamadas = new CopyOnWriteArrayList<>();

    private ChatModel lentoNoPrincipal(long dormirMs, String json) {
        return prompt -> {
            String nome = prompt.getOptions() == null || prompt.getOptions().getModel() == null
                    ? "principal"
                    : prompt.getOptions().getModel();
            chamadas.add(nome);
            if (nome.equals("principal")) {
                try {
                    Thread.sleep(dormirMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        };
    }

    private GeminiBuildGeneratorAdapter adapter(ChatModel model, Duration porChamada) {
        // Relógio real: o prazo é comparado com o tempo que de fato passou.
        return new GeminiBuildGeneratorAdapter(
                ChatClient.builder(model), Instant::now, new AiProperties(porChamada, List.of("gemini-3.6-flash")));
    }

    @Test
    @DisplayName("a chamada é cortada no prazo da geração, mesmo com timeout por modelo maior")
    void chamadaCortadaNoPrazo() {
        var adapter = adapter(lentoNoPrincipal(3_000, "{}"), Duration.ofSeconds(180));
        long inicio = System.nanoTime();

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of(), Instant.now().plusMillis(600)))
                .isInstanceOf(ProviderUnavailable.class);

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(1_800));
        assertThat(chamadas).as("sem tempo sobrando, o reserva não é chamado").containsExactly("principal");
    }

    @Test
    @DisplayName("com tempo sobrando, o reserva recebe o que resta do prazo")
    void reservaRecebeOQueResta() throws IOException {
        String json = new ClassPathResource("fixtures/gemini/build-response-valid.json").getContentAsString(StandardCharsets.UTF_8);
        // Principal estoura o próprio timeout (300 ms); sobra tempo no prazo de 3 s para o reserva.
        var adapter = adapter(lentoNoPrincipal(2_000, json), Duration.ofMillis(300));

        assertThat(adapter.generate(pedido, slice, List.of(), Instant.now().plusSeconds(3)).mainSkill()).isEqualTo("Ice Shot");
        assertThat(chamadas).containsExactly("principal", "gemini-3.6-flash");
    }

    @Test
    @DisplayName("prazo já vencido: nenhuma chamada ao provedor")
    void prazoVencido() {
        var adapter = adapter(lentoNoPrincipal(0, "{}"), Duration.ofSeconds(180));

        assertThatThrownBy(() -> adapter.generate(pedido, slice, List.of(), Instant.now().minusSeconds(1)))
                .isInstanceOf(ProviderUnavailable.class);
        assertThat(chamadas).isEmpty();
    }
}
