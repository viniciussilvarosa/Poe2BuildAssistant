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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O prazo total da geração (5 min) vale para todas as tentativas (pedido do dono do projeto, PR #90). */
class GenerateBuildDeadlineTest {

    private static final Instant START = Instant.parse("2026-11-22T15:00:00Z");
    private static final Duration BUDGET = Duration.ofSeconds(270); // 5 min - 30 s para gravar e responder

    private Instant now = START;
    private final List<Instant> deadlines = new ArrayList<>();

    /** Gerador que registra o prazo recebido e "gasta" tempo devolvendo um rascunho inválido. */
    private final BuildGeneratorPort lentoEInvalido = new BuildGeneratorPort() {
        @Override
        public GeneratedBuildDraft generate(BuildRequest request, CatalogSlice slice, List<BuildViolation> feedback) {
            throw new AssertionError("o serviço deve passar o prazo ao gerador");
        }

        @Override
        public GeneratedBuildDraft generate(
                BuildRequest request, CatalogSlice slice, List<BuildViolation> feedback, Instant deadline) {
            deadlines.add(deadline);
            now = now.plusSeconds(200); // a tentativa levou 200 s
            return CatalogFixtures.draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Fake Support"), CatalogFixtures.FIVE_NOTABLES);
        }
    };

    private GenerateBuildService service() {
        return new GenerateBuildService(
                CatalogFixtures::catalog,
                lentoEInvalido,
                new GenerateBuildServiceTest.FakeQuota(),
                new GenerateBuildServiceTest.FakeBuilds(),
                () -> now,
                new QuotaPolicy(3, 10),
                new GenerationSettings(2, 50, BUDGET));
    }

    @Test
    @DisplayName("o gerador recebe o prazo: início + orçamento da geração")
    void prazoRepassado() {
        assertThatThrownBy(() -> service().generate(
                        new GenerateBuildCommand(new BuildRequest("arqueiro", Constraints.none()), Requester.anonymousFromIp("203.0.113.7", "salt")),
                        step -> {}))
                .isInstanceOf(GenerationFailed.class);

        assertThat(deadlines.getFirst()).isEqualTo(START.plus(BUDGET));
    }

    @Test
    @DisplayName("prazo vencido depois da 1ª tentativa: não começa a 2ª e termina como falha de geração")
    void naoComecaTentativaForaDoPrazo() {
        // 1ª tentativa termina em 200 s; a 2ª teria só 70 s, ainda dentro: começa. Com 2 tentativas de 200 s,
        // a 3ª nunca começaria. Aqui, com orçamento de 150 s, a 2ª já não pode começar.
        var curto = new GenerateBuildService(
                CatalogFixtures::catalog,
                lentoEInvalido,
                new GenerateBuildServiceTest.FakeQuota(),
                new GenerateBuildServiceTest.FakeBuilds(),
                () -> now,
                new QuotaPolicy(3, 10),
                new GenerationSettings(2, 50, Duration.ofSeconds(150)));

        assertThatThrownBy(() -> curto.generate(
                        new GenerateBuildCommand(new BuildRequest("arqueiro", Constraints.none()), Requester.anonymousFromIp("203.0.113.8", "salt")),
                        step -> {}))
                .isInstanceOf(GenerationFailed.class);

        assertThat(deadlines).as("só a 1ª tentativa chamou o gerador").hasSize(1);
    }
}
