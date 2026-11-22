package com.poe.poe2buildassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import com.poe.poe2buildassistant.adapter.in.web.build.GenerationStreamTimeout;
import com.poe.poe2buildassistant.adapter.out.ai.AiProperties;
import com.poe.poe2buildassistant.application.service.GenerationSettings;
import org.flywaydb.core.Flyway;
import org.springframework.ai.chat.model.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ApplicationContextTest {

    @Autowired private Flyway flyway;
    @Autowired private ChatModel chatModel;
    @Autowired private AiProperties ai;
    @Autowired private GenerationSettings generation;
    @Autowired private GenerationStreamTimeout streamTimeout;

    @Test
    @DisplayName("timeout da IA de 60 s, e o stream SSE fica aberto além do pior caso da geração (#46, R-01)")
    void timeoutsAlinhados() {
        assertThat(ai.timeout()).isEqualTo(Duration.ofSeconds(60));
        // Limite de 5 min da geração (#88), cobrindo cada tentativa no principal e no reserva.
        assertThat(streamTimeout.value()).isEqualTo(Duration.ofMinutes(5));
        assertThat(streamTimeout.value()).isGreaterThan(ai.timeout().multipliedBy(generation.maxAttempts() * 2L));
        assertThat(ai.fallbackModel()).isEqualTo("gemini-3.5-flash");
    }

    @Test
    @DisplayName("o modelo do Gemini é fixado na configuração, não herdado do Spring AI (bug #43)")
    void modeloFixado() {
        // O padrão do Spring AI 2.0.1, gemini-2.5-flash, foi descontinuado para chaves novas (404).
        assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo("gemini-3.6-flash");
    }

    @Test
    @DisplayName("o console fora de produção mostra os pares chave-valor dos eventos (bug #51)")
    void logMostraChaveValor(CapturedOutput output) {
        LoggerFactory.getLogger("qa.teste")
                .atInfo()
                .setMessage("evento.de.teste")
                .addKeyValue("status", 404)
                .addKeyValue("violationTypes", List.of("UNKNOWN_ITEM"))
                .log();

        // Texto (status="404") ou JSON ECS ("status":404), conforme o formato ativo na suíte: os pares aparecem.
        assertThat(output).contains("evento.de.teste").containsAnyOf("status=\"404\"", "\"status\":404").contains("UNKNOWN_ITEM");
    }

    @Test
    @DisplayName("contexto sobe com perfil test e PostgreSQL, sem migrations pendentes ou com erro")
    void contextoSobeEFlywayAplicaMigrations() {
        var info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.all()).isNotEmpty().allSatisfy(m -> {
            assertThat(m.getState().isApplied()).as("migration %s aplicada", m.getVersion()).isTrue();
            assertThat(m.getState().isFailed()).as("migration %s sem erro", m.getVersion()).isFalse();
        });
        assertThat(info.current().getVersion()).isEqualTo(info.all()[info.all().length - 1].getVersion());
    }
}
