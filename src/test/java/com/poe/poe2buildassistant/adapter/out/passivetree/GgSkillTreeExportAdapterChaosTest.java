package com.poe.poe2buildassistant.adapter.out.passivetree;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.poe.poe2buildassistant.domain.shared.PassiveTreeUnavailable;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ataques ao download do export (card #6, PR #35): servidor local hostil, sem rede externa. */
class GgSkillTreeExportAdapterChaosTest {

    @Test
    @DisplayName("N4 — servidor que aceita a conexão e nunca responde não prende o import para sempre")
    void servidorMudo() throws Exception {
        CountDownLatch fimDoTeste = new CountDownLatch(1);
        HttpServer mudo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mudo.createContext("/data.json", exchange -> {
            try {
                fimDoTeste.await(5, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        mudo.start();
        try {
            String url = "http://127.0.0.1:" + mudo.getAddress().getPort() + "/data.json";
            // Configuração padrão de produção: só a URL muda.
            GgSkillTreeExportAdapter adapter = new GgSkillTreeExportAdapter(PassiveTreeProperties.withDefaultTimeouts(url));

            assertTimeoutPreemptively(Duration.ofSeconds(35), () ->
                    assertThatThrownBy(adapter::fetchNotables).isInstanceOf(PassiveTreeUnavailable.class));
        } finally {
            fimDoTeste.countDown();
            mudo.stop(0);
        }
    }
}
