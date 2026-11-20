package com.poe.poe2buildassistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase.GenerateBuildCommand;
import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.GenerationProgressPort;
import com.poe.poe2buildassistant.application.service.GenerateBuildServiceTest.FakeBuilds;
import com.poe.poe2buildassistant.application.service.GenerateBuildServiceTest.FakeGenerator;
import com.poe.poe2buildassistant.application.service.GenerateBuildServiceTest.FakeQuota;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GenerationStep;
import com.poe.poe2buildassistant.domain.quota.QuotaPolicy;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Ataques ao caso de uso de geração (card #10): o usuário nunca paga cota sem receber build. */
class GenerateBuildServiceChaosTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");

    private final FakeGenerator generator = new FakeGenerator();
    private final FakeQuota quota = new FakeQuota();
    private final FakeBuilds builds = new FakeBuilds();
    private final Requester visitante = Requester.anonymousFromIp("203.0.113.7", "salt");

    private GenerateBuildService service(BuildRepositoryPort repository) {
        return new GenerateBuildService(
                CatalogFixtures::catalog, generator, quota, repository, () -> NOW, new QuotaPolicy(3, 10), new GenerationSettings(2, 50));
    }

    private static GenerateBuildCommand command(Requester requester) {
        return new GenerateBuildCommand(new BuildRequest("arqueiro mágico", Constraints.none()), requester);
    }

    @Nested
    @DisplayName("S1/S2 — falha inesperada depois de cobrar devolve a cota")
    class FalhaInesperada {

        @Test
        @DisplayName("S1: erro inesperado do gerador devolve a cota e é repassado")
        void erroInesperadoDoGerador() {
            generator.respond(() -> {
                throw new IllegalStateException("bug no adaptador");
            });

            assertThatThrownBy(() -> service(builds).generate(command(visitante), GenerationProgressPort.none()))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(quota.used(visitante)).isZero();
        }

        @Test
        @DisplayName("S2: falha ao salvar uma build já validada devolve a cota")
        void falhaAoSalvar() {
            generator.respond(CatalogFixtures::validDraft);
            BuildRepositoryPort bancoFora = new BuildRepositoryPort() {
                @Override
                public Build save(Build build) {
                    throw new IllegalStateException("banco fora do ar");
                }

                @Override
                public Optional<Build> findById(UUID id) {
                    return Optional.empty();
                }

                @Override
                public List<Build> findByOwner(String ownerId) {
                    return List.of();
                }
            };

            assertThatThrownBy(() -> service(bancoFora).generate(command(visitante), GenerationProgressPort.none()))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(quota.used(visitante)).isZero();
        }
    }

    @Nested
    @DisplayName("S3 — ouvinte de progresso quebrado não interrompe a geração")
    class OuvinteQuebrado {

        @ParameterizedTest(name = "ouvinte que falha em {0}: a build é salva e devolvida")
        @EnumSource(GenerationStep.class)
        void ouvinteFalhaNumaEtapa(GenerationStep etapaQueFalha) {
            generator.respond(CatalogFixtures::validDraft);
            GenerationProgressPort desconectado = step -> {
                if (step == etapaQueFalha) {
                    throw new IllegalStateException("cliente SSE desconectado");
                }
            };

            Build build = service(builds).generate(command(visitante), desconectado);

            assertThat(builds.saved).containsExactly(build);
            assertThat(quota.used(visitante)).isEqualTo(1);
        }

        @Test
        @DisplayName("S3b: ouvinte quebrado desde a primeira etapa ainda recebe a build salva")
        void ouvinteSempreQuebrado() {
            generator.respond(CatalogFixtures::validDraft);
            GenerationProgressPort morto = step -> {
                throw new IllegalStateException("cliente já saiu");
            };

            Build build = service(builds).generate(command(visitante), morto);

            assertThat(builds.saved).containsExactly(build);
        }
    }
}
