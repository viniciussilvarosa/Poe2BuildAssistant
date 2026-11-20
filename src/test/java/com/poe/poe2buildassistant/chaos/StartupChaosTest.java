package com.poe.poe2buildassistant.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.poe.poe2buildassistant.Poe2BuildAssistantApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Ataques ao startup: ausência de segredos, vazamento em logs e formato do evento app.started.
 * Sobe a aplicação real (servidor em porta aleatória) contra um PostgreSQL descartável.
 */
@ExtendWith(OutputCaptureExtension.class)
class StartupChaosTest {

    // Valores 100% sintéticos, usados só para detectar vazamento.
    private static final String FAKE_GEMINI_KEY = "chaos-fake-gemini-key-7f3a9c";
    private static final String FAKE_DB_PASSWORD = "chaos-fake-db-pass-51e2d8";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18-alpine").withPassword(FAKE_DB_PASSWORD);

    @BeforeAll
    static void start() {
        assumeTrue(
                System.getenv("GEMINI_API_KEY") == null && System.getenv("DB_PASSWORD") == null,
                "ambiente já define GEMINI_API_KEY/DB_PASSWORD; ataques de ausência seriam inválidos");
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    private static ConfigurableApplicationContext run(String profile, String... extraProps) {
        // Argumentos de linha de comando: precedência acima do application.properties.
        List<String> args = new ArrayList<>(List.of(
                "--server.port=0",
                "--DB_URL=" + POSTGRES.getJdbcUrl(),
                "--DB_USERNAME=" + POSTGRES.getUsername(),
                "--DB_PASSWORD=" + FAKE_DB_PASSWORD));
        for (String p : extraProps) {
            args.add("--" + p);
        }
        return new SpringApplicationBuilder(Poe2BuildAssistantApplication.class)
                .profiles(profile)
                .run(args.toArray(String[]::new));
    }

    @Test
    @DisplayName("A6: sem GEMINI_API_KEY o startup falha e o relatório de falha cita a variável")
    void semChaveFalhaNoStartup(CapturedOutput output) {
        assertThatThrownBy(() -> run("dev").close())
                .as("a aplicação não deveria subir sem a chave do Gemini")
                .isNotNull();
        assertThat(output.getAll())
                .as("o desenvolvedor precisa ver qual variável definir")
                .contains("APPLICATION FAILED TO START")
                .contains("GEMINI_API_KEY");
    }

    @Test
    @DisplayName("A6b: sem DB_PASSWORD o startup falha (banco com autenticação por senha)")
    void semSenhaDoBancoFalhaNoStartup() {
        assertThatThrownBy(
                        () ->
                                new SpringApplicationBuilder(Poe2BuildAssistantApplication.class)
                                        .profiles("dev")
                                        .run(
                                                "--server.port=0",
                                                "--DB_URL=" + POSTGRES.getJdbcUrl(),
                                                "--DB_USERNAME=" + POSTGRES.getUsername(),
                                                "--GEMINI_API_KEY=" + FAKE_GEMINI_KEY)
                                        .close())
                .as("a aplicação não deveria subir sem a senha do banco")
                .isNotNull();
    }

    @Test
    @DisplayName("A7: GEMINI_API_KEY vazia (.env.example copiado sem preencher) falha no startup")
    void chaveVaziaFalhaNoStartup() {
        assertThatThrownBy(() -> run("dev", "GEMINI_API_KEY=").close())
                .as("chave vazia deveria falhar cedo, não na primeira chamada ao Gemini")
                .isNotNull();
    }

    @Test
    @DisplayName("A8: nenhum segredo aparece nos logs de startup (perfil dev, DEBUG)")
    void segredosNaoVazamNosLogs(CapturedOutput output) {
        try (var ctx = run("dev", "GEMINI_API_KEY=" + FAKE_GEMINI_KEY)) {
            assertThat(ctx.isActive()).isTrue();
        }
        assertThat(output.getAll()).doesNotContain(FAKE_GEMINI_KEY).doesNotContain(FAKE_DB_PASSWORD);
    }

    @Test
    @DisplayName("A9: no perfil prod, app.started sai em JSON (ECS) com perfil e versão")
    void appStartedEstruturadoEmProd(CapturedOutput output) {
        try (var ctx = run("prod", "GEMINI_API_KEY=" + FAKE_GEMINI_KEY)) {
            assertThat(ctx.isActive()).isTrue();
        }
        String linha =
                output.getAll().lines()
                        .filter(l -> l.contains("app.started"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("evento app.started não encontrado"));
        assertThat(linha).startsWith("{").contains("\"message\":\"app.started\"").contains("prod").contains("0.0.1-SNAPSHOT");
        assertThat(output.getAll()).doesNotContain(FAKE_GEMINI_KEY).doesNotContain(FAKE_DB_PASSWORD);
    }
}
