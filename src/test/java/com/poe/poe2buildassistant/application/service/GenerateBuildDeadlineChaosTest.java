package com.poe.poe2buildassistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase.GenerateBuildCommand;
import com.poe.poe2buildassistant.application.port.out.BuildGeneratorPort;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.BuildViolation;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import com.poe.poe2buildassistant.domain.quota.QuotaPolicy;
import com.poe.poe2buildassistant.domain.quota.Requester;
import com.poe.poe2buildassistant.domain.shared.GenerationFailed;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D4 (caos do PR #90): resposta malformada que consome o prazo não abre nova tentativa. */
class GenerateBuildDeadlineChaosTest {

    private Instant now = Instant.parse("2026-11-22T15:00:00Z");
    private final AtomicInteger chamadas = new AtomicInteger();

    @Test
    @DisplayName("1ª tentativa com JSON malformado gasta o prazo: a 2ª não começa e a falha é de geração")
    void malformadoForaDoPrazo() {
        BuildGeneratorPort malformadoLento = new BuildGeneratorPort() {
            @Override
            public GeneratedBuildDraft generate(BuildRequest r, CatalogSlice s, List<BuildViolation> f) {
                throw new AssertionError("o serviço deve passar o prazo");
            }

            @Override
            public GeneratedBuildDraft generate(BuildRequest r, CatalogSlice s, List<BuildViolation> f, Instant deadline) {
                chamadas.incrementAndGet();
                now = now.plusSeconds(280); // passou do prazo de 270 s
                throw new GenerationFailed();
            }
        };
        var service = new GenerateBuildService(
                CatalogFixtures::catalog,
                malformadoLento,
                new GenerateBuildServiceTest.FakeQuota(),
                new GenerateBuildServiceTest.FakeBuilds(),
                () -> now,
                new QuotaPolicy(3, 10),
                new GenerationSettings(2, 50, Duration.ofSeconds(270)));

        assertThatThrownBy(() -> service.generate(
                        new GenerateBuildCommand(new BuildRequest("arqueiro", Constraints.none()), Requester.anonymousFromIp("203.0.113.9", "salt")),
                        step -> {}))
                .isInstanceOf(GenerationFailed.class);
        assertThat(chamadas).as("a 2ª tentativa não começa fora do prazo").hasValue(1);
    }
}
