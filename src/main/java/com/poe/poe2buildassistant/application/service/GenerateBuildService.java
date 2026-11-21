package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase;
import com.poe.poe2buildassistant.application.port.out.BuildGeneratorPort;
import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.ClockPort;
import com.poe.poe2buildassistant.application.port.out.GenerationProgressPort;
import com.poe.poe2buildassistant.application.port.out.QuotaCounterPort;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.BuildValidator;
import com.poe.poe2buildassistant.domain.build.DraftRepair;
import com.poe.poe2buildassistant.domain.build.BuildViolation;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import com.poe.poe2buildassistant.domain.build.GenerationStep;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.quota.QuotaPolicy;
import com.poe.poe2buildassistant.domain.quota.Requester;
import com.poe.poe2buildassistant.domain.shared.GenerationFailed;
import com.poe.poe2buildassistant.domain.shared.ProviderUnavailable;
import com.poe.poe2buildassistant.domain.shared.QuotaExceeded;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso central: catálogo → validação do pedido → cota → recorte → geração → validação da
 * saída → regeneração com feedback → persistência. Garante a regra dos 100%: só uma build com
 * zero violações é salva e devolvida.
 *
 * <p>Sem {@code @Transactional} aqui de propósito: a chamada ao LLM pode levar até o app.ai.timeout (60 s) e não pode
 * segurar uma conexão do pool. A transação fica só na persistência.
 */
@Service
public class GenerateBuildService implements GenerateBuildUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateBuildService.class);

    private final CatalogRepositoryPort catalogs;
    private final BuildGeneratorPort generator;
    private final QuotaCounterPort quota;
    private final BuildRepositoryPort builds;
    private final ClockPort clock;
    private final QuotaPolicy quotaPolicy;
    private final GenerationSettings settings;
    private final BuildValidator validator = new BuildValidator();
    private final DraftRepair repair = new DraftRepair();

    public GenerateBuildService(
            CatalogRepositoryPort catalogs,
            BuildGeneratorPort generator,
            QuotaCounterPort quota,
            BuildRepositoryPort builds,
            ClockPort clock,
            QuotaPolicy quotaPolicy,
            GenerationSettings settings) {
        this.catalogs = catalogs;
        this.generator = generator;
        this.quota = quota;
        this.builds = builds;
        this.clock = clock;
        this.quotaPolicy = quotaPolicy;
        this.settings = settings;
    }

    @Override
    public Build generate(GenerateBuildCommand command, GenerationProgressPort listener) {
        Instant start = clock.now();
        BuildRequest request = command.request();
        Requester requester = command.requester();
        GenerationProgressPort progress = isolated(listener);

        // Validar antes de cobrar: pedido incoerente ou catálogo ausente não gastam cota.
        progress.emit(GenerationStep.VALIDATING_INPUT);
        Catalog catalog = catalogs.loadPublished();
        request.validateAgainst(catalog);

        LocalDate day = quotaPolicy.dayOf(start);
        if (!quota.tryConsume(requester, day, quotaPolicy.limitFor(requester))) {
            throw new QuotaExceeded(quotaPolicy.nextReset(start));
        }

        try {
            Build build = generateValid(request, requester, catalog, progress, start);
            log.atInfo()
                    .setMessage("build.generation.completed")
                    .addKeyValue("durationMs", Duration.between(start, clock.now()).toMillis())
                    .addKeyValue("requesterType", requester.type())
                    .addKeyValue("catalogPatch", catalog.version().patch())
                    .log();
            return build;
        } catch (GenerationFailed e) {
            // A IA respondeu e nenhuma tentativa serviu: houve geração, a cota fica consumida.
            throw e;
        } catch (RuntimeException e) {
            // Provedor indisponível, bug ou banco fora do ar: não é culpa do usuário, a cota volta
            // (card #10; chaos T2/T3).
            quota.release(requester, day);
            logFailure(e instanceof ProviderUnavailable ? "provider_unavailable" : "unexpected", requester);
            throw e;
        }
    }

    /**
     * Progresso é só informativo: um ouvinte que falha (ex.: cliente SSE que fechou a aba) nunca
     * interrompe a geração nem muda o resultado. Depois da primeira falha ele é ignorado (chaos T1).
     */
    private static GenerationProgressPort isolated(GenerationProgressPort listener) {
        AtomicBoolean broken = new AtomicBoolean(false);
        return step -> {
            if (broken.get()) {
                return;
            }
            try {
                listener.emit(step);
            } catch (RuntimeException e) {
                broken.set(true);
                log.atWarn().setMessage("build.generation.progress_listener_failed").addKeyValue("step", step).log();
            }
        };
    }

    private Build generateValid(
            BuildRequest request, Requester requester, Catalog catalog, GenerationProgressPort progress, Instant start) {
        CatalogSlice slice = CatalogSlice.of(catalog, request, settings.sliceMaxItems());
        List<BuildViolation> feedback = List.of();
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            progress.emit(GenerationStep.GENERATING);
            GeneratedBuildDraft draft;
            try {
                draft = generator.generate(request, slice, feedback);
            } catch (GenerationFailed e) {
                log.atWarn().setMessage("build.generation.malformed_attempt").addKeyValue("attempt", attempt).log();
                continue; // resposta inutilizável conta como tentativa; tenta de novo com o mesmo feedback
            }

            progress.emit(GenerationStep.VALIDATING_OUTPUT);
            DraftRepair.Result repaired = repair.repair(draft, catalog);
            draft = repaired.draft();
            if (repaired.changed()) {
                log.atInfo()
                        .setMessage("build.generation.draft_repaired")
                        .addKeyValue("attempt", attempt)
                        .addKeyValue("corrected", repaired.corrected())
                        .addKeyValue("dropped", repaired.dropped())
                        .log();
            }
            List<BuildViolation> violations = validator.validate(draft, catalog, request);
            if (violations.isEmpty()) {
                progress.emit(GenerationStep.SAVING);
                Optional<String> owner = requester instanceof Requester.Authenticated u
                        ? Optional.of(u.userId())
                        : Optional.empty();
                Build saved = builds.save(new Build(UUID.randomUUID(), owner, catalog.version().id(), request, draft, start));
                progress.emit(GenerationStep.DONE);
                return saved;
            }
            log.atWarn()
                    .setMessage("build.generation.invalid_draft")
                    .addKeyValue("attempt", attempt)
                    .addKeyValue("violationTypes", violations.stream().map(v -> v.type().name()).distinct().toList())
                    .addKeyValue("corrected", repaired.corrected())
                    .addKeyValue("dropped", repaired.dropped())
                    .log();
            feedback = violations;
        }
        logFailure("max_attempts", requester);
        throw new GenerationFailed();
    }

    private static void logFailure(String reason, Requester requester) {
        log.atError()
                .setMessage("build.generation.failed")
                .addKeyValue("reason", reason)
                .addKeyValue("requesterType", requester.type())
                .log();
    }
}
