package com.poe.poe2buildassistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase.GenerateBuildCommand;
import com.poe.poe2buildassistant.application.port.out.BuildGeneratorPort;
import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.GenerationProgressPort;
import com.poe.poe2buildassistant.application.port.out.QuotaCounterPort;
import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.BuildViolation;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import com.poe.poe2buildassistant.domain.build.GenerationStep;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.quota.QuotaPolicy;
import com.poe.poe2buildassistant.domain.quota.Requester;
import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import com.poe.poe2buildassistant.domain.shared.GenerationFailed;
import com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest;
import com.poe.poe2buildassistant.domain.shared.ProviderUnavailable;
import com.poe.poe2buildassistant.domain.shared.QuotaExceeded;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerateBuildServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");

    private final FakeGenerator generator = new FakeGenerator();
    private final FakeQuota quota = new FakeQuota();
    private final FakeBuilds builds = new FakeBuilds();
    private final RecordingProgress progress = new RecordingProgress();
    private Catalog catalog = CatalogFixtures.catalog();
    private final CatalogRepositoryPort catalogs = () -> {
        if (catalog == null) {
            throw new CatalogNotPublished();
        }
        return catalog;
    };

    private final GenerateBuildService service = new GenerateBuildService(
            catalogs, generator, quota, builds, () -> NOW, new QuotaPolicy(3, 10), new GenerationSettings(2, 50));

    private final Requester visitante = Requester.anonymousFromIp("203.0.113.7", "salt");
    private final Requester logado = new Requester.Authenticated("42");

    private static GenerateBuildCommand command(String concept, Constraints constraints, Requester requester) {
        return new GenerateBuildCommand(new BuildRequest(concept, constraints), requester);
    }

    private static GeneratedBuildDraft invalido() {
        return CatalogFixtures.draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Fake Support"), CatalogFixtures.FIVE_NOTABLES);
    }

    @Test
    @DisplayName("visitante com só o conceito recebe build válida e salva sem dono")
    void visitanteSoConceito() {
        generator.respond(CatalogFixtures::validDraft);

        Build build = service.generate(command("arqueiro mágico", Constraints.none(), visitante), progress);

        assertThat(build.ownerId()).isEmpty();
        assertThat(build.catalogVersionId()).isEqualTo(1L);
        assertThat(build.request().concept()).isEqualTo("arqueiro mágico");
        assertThat(build.createdAt()).isEqualTo(NOW);
        assertThat(builds.saved).containsExactly(build);
        assertThat(progress.steps).containsExactly(
                GenerationStep.VALIDATING_INPUT, GenerationStep.GENERATING, GenerationStep.VALIDATING_OUTPUT,
                GenerationStep.SAVING, GenerationStep.DONE);
        assertThat(quota.used(visitante)).isEqualTo(1);
    }

    @Test
    @DisplayName("restrições do usuário são respeitadas: Ranger + Bow")
    void restricoesRespeitadas() {
        generator.respond(CatalogFixtures::validDraft);

        Build build = service.generate(
                command("arqueiro", Constraints.none().withCharacterClass("Ranger").withWeaponType("Bow"), visitante), progress);

        assertThat(build.content().characterClass()).isEqualTo("Ranger");
        assertThat(build.content().weaponType()).isEqualTo("Bow");
        assertThat(generator.requests.getFirst().constraints().characterClass()).contains("Ranger");
    }

    @Test
    @DisplayName("draft inválido é regenerado com as violações como feedback")
    void regeneraComFeedback() {
        generator.respond(GenerateBuildServiceTest::invalido);
        generator.respond(CatalogFixtures::validDraft);

        Build build = service.generate(command("arqueiro", Constraints.none(), visitante), progress);

        assertThat(generator.calls()).isEqualTo(2);
        assertThat(generator.feedbacks.get(0)).isEmpty();
        assertThat(generator.feedbacks.get(1)).extracting(BuildViolation::itemName).contains("Fake Support");
        assertThat(build.content().supports()).doesNotContain("Fake Support");
        assertThat(quota.used(visitante)).isEqualTo(1);
    }

    @Test
    @DisplayName("suporte desconhecido com válidos suficientes é consertado sem nova chamada à IA (QA-02)")
    void consertaSemRegenerar() {
        generator.respond(() -> CatalogFixtures.draft(
                "Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce", "Mirage Archer Support"), CatalogFixtures.FIVE_NOTABLES));

        Build build = service.generate(command("arqueiro", Constraints.none(), visitante), progress);

        assertThat(generator.calls()).isEqualTo(1);
        assertThat(build.content().supports()).containsExactly("Pierce");
    }

    @Test
    @DisplayName("após o máximo de tentativas lança GenerationFailed e não salva nada")
    void maximoDeTentativas() {
        generator.respond(GenerateBuildServiceTest::invalido);
        generator.respond(GenerateBuildServiceTest::invalido);
        generator.respond(CatalogFixtures::validDraft); // nunca deve ser pedido: máximo = 2

        assertThatThrownBy(() -> service.generate(command("arqueiro", Constraints.none(), visitante), progress))
                .isInstanceOf(GenerationFailed.class);
        assertThat(generator.calls()).isEqualTo(2);
        assertThat(builds.saved).isEmpty();
        assertThat(quota.used(visitante)).isEqualTo(1);
        assertThat(progress.steps).doesNotContain(GenerationStep.SAVING, GenerationStep.DONE);
    }

    @Test
    @DisplayName("resposta malformada conta como tentativa e a próxima pode dar certo")
    void malformadoContaComoTentativa() {
        generator.respond(() -> {
            throw new GenerationFailed();
        });
        generator.respond(CatalogFixtures::validDraft);

        assertThat(service.generate(command("arqueiro", Constraints.none(), visitante), progress)).isNotNull();
        assertThat(generator.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("cota esgotada bloqueia antes de chamar o gerador")
    void cotaEsgotada() {
        quota.setUsed(visitante, 3);

        assertThatThrownBy(() -> service.generate(command("arqueiro", Constraints.none(), visitante), progress))
                .isInstanceOfSatisfying(
                        QuotaExceeded.class, e -> assertThat(e.retryAt()).isEqualTo(Instant.parse("2026-11-22T00:00:00Z")));
        assertThat(generator.calls()).isZero();
    }

    @Test
    @DisplayName("usuário logado: build salva com dono e usa o limite maior")
    void logadoSalvaComDono() {
        quota.setUsed(logado, 5);
        generator.respond(CatalogFixtures::validDraft);

        Build build = service.generate(command("arqueiro", Constraints.none(), logado), progress);

        assertThat(build.ownerId()).contains("42");
    }

    @Test
    @DisplayName("provedor indisponível devolve a cota e repassa o horário de retorno")
    void provedorIndisponivelDevolveCota() {
        generator.respond(() -> {
            throw new ProviderUnavailable(Optional.of(NOW.plusSeconds(60)));
        });

        assertThatThrownBy(() -> service.generate(command("arqueiro", Constraints.none(), visitante), progress))
                .isInstanceOf(ProviderUnavailable.class);
        assertThat(quota.used(visitante)).isZero();
        assertThat(generator.calls()).isEqualTo(1);
    }

    @Test
    @DisplayName("pedido inválido para o catálogo é recusado sem gastar cota")
    void pedidoInvalidoNaoGastaCota() {
        assertThatThrownBy(() -> service.generate(
                        command("arqueiro", Constraints.none().withCharacterClass("Ranger").withAscendancy("Infernalist"), visitante),
                        progress))
                .isInstanceOf(InvalidBuildRequest.class);
        assertThat(quota.used(visitante)).isZero();
        assertThat(generator.calls()).isZero();
    }

    @Test
    @DisplayName("sem catálogo publicado não gasta cota")
    void semCatalogoNaoGastaCota() {
        catalog = null;

        assertThatThrownBy(() -> service.generate(command("arqueiro", Constraints.none(), visitante), progress))
                .isInstanceOf(CatalogNotPublished.class);
        assertThat(quota.used(visitante)).isZero();
    }

    @Test
    @DisplayName("o gerador recebe o recorte do catálogo coerente com as restrições")
    void geradorRecebeRecorte() {
        generator.respond(CatalogFixtures::validDraft);

        service.generate(command("arqueiro", Constraints.none().withMainSkill("Ice Shot"), visitante), progress);

        assertThat(generator.slices.getFirst().skillGems()).extracting(g -> g.name()).containsExactly("Ice Shot");
    }

    // ---- fakes ----

    static final class FakeGenerator implements BuildGeneratorPort {
        private final Deque<Supplier<GeneratedBuildDraft>> responses = new ArrayDeque<>();
        final List<BuildRequest> requests = new ArrayList<>();
        final List<CatalogSlice> slices = new ArrayList<>();
        final List<List<BuildViolation>> feedbacks = new ArrayList<>();

        void respond(Supplier<GeneratedBuildDraft> response) {
            responses.add(response);
        }

        int calls() {
            return requests.size();
        }

        @Override
        public GeneratedBuildDraft generate(BuildRequest request, CatalogSlice slice, List<BuildViolation> feedback) {
            requests.add(request);
            slices.add(slice);
            feedbacks.add(feedback);
            return responses.removeFirst().get();
        }
    }

    static final class FakeQuota implements QuotaCounterPort {
        private final Map<String, Integer> counts = new HashMap<>();

        void setUsed(Requester r, int used) {
            counts.put(r.key(), used);
        }

        int used(Requester r) {
            return counts.getOrDefault(r.key(), 0);
        }

        @Override
        public boolean tryConsume(Requester r, LocalDate day, int limit) {
            if (used(r) >= limit) {
                return false;
            }
            counts.merge(r.key(), 1, Integer::sum);
            return true;
        }

        @Override
        public void release(Requester r, LocalDate day) {
            counts.computeIfPresent(r.key(), (k, v) -> Math.max(0, v - 1));
        }

        @Override
        public int current(Requester r, LocalDate day) {
            return used(r);
        }
    }

    static final class FakeBuilds implements BuildRepositoryPort {
        final List<Build> saved = new ArrayList<>();

        @Override
        public Build save(Build build) {
            saved.add(build);
            return build;
        }

        @Override
        public Optional<Build> findById(UUID id) {
            return saved.stream().filter(b -> b.id().equals(id)).findFirst();
        }

        @Override
        public List<Build> findByOwner(String ownerId) {
            return saved.stream().filter(b -> b.ownerId().equals(Optional.of(ownerId))).toList();
        }
    }

    static final class RecordingProgress implements GenerationProgressPort {
        final List<GenerationStep> steps = new ArrayList<>();

        @Override
        public void emit(GenerationStep step) {
            steps.add(step);
        }
    }
}
