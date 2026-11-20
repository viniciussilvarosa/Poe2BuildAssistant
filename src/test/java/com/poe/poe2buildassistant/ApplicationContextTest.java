package com.poe.poe2buildassistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key-ficticia")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApplicationContextTest {

    @Autowired private Flyway flyway;

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
