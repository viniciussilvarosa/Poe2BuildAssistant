package com.poe.poe2buildassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
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
