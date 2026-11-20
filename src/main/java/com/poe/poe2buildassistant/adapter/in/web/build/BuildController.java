package com.poe.poe2buildassistant.adapter.in.web.build;

import com.poe.poe2buildassistant.adapter.in.web.ApiProblems;
import com.poe.poe2buildassistant.adapter.in.web.RequesterResolver;
import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase;
import com.poe.poe2buildassistant.application.port.in.GenerateBuildUseCase.GenerateBuildCommand;
import com.poe.poe2buildassistant.application.port.in.GetBuildUseCase;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.quota.Requester;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API de geração. O POST valida o corpo antes de abrir o stream (erros viram 400 comuns) e depois
 * responde em SSE: eventos {@code progress}, e por fim {@code done} com a build ou {@code error}
 * com o problema em pt-BR.
 */
@RestController
@RequestMapping("/api/builds")
class BuildController {

    static final String EXECUTOR = "buildGenerationExecutor";
    /** Acima do timeout do LLM (25 s) vezes as tentativas, com folga para validação e gravação. */
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(90);
    private static final Logger log = LoggerFactory.getLogger(BuildController.class);

    private final GenerateBuildUseCase generateBuild;
    private final GetBuildUseCase getBuild;
    private final RequesterResolver requesters;
    private final Executor executor;

    BuildController(
            GenerateBuildUseCase generateBuild,
            GetBuildUseCase getBuild,
            RequesterResolver requesters,
            @Qualifier(EXECUTOR) Executor executor) {
        this.generateBuild = generateBuild;
        this.getBuild = getBuild;
        this.requesters = requesters;
        this.executor = executor;
    }

    @PostMapping("/generate")
    SseEmitter generate(@RequestBody GenerateBuildRequestDto body, HttpServletRequest http) {
        BuildRequest request = body.toDomain();
        Requester requester = requesters.resolve(http);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        SseProgress progress = new SseProgress(emitter);
        log.atInfo().setMessage("http.build.generate.started").addKeyValue("requesterType", requester.type()).log();

        executor.execute(() -> {
            int status = HttpStatus.OK.value();
            try {
                Build build = generateBuild.generate(new GenerateBuildCommand(request, requester), progress);
                // Se o cliente saiu, a build já está salva (link em "Minhas builds"); só registra.
                sendQuietly(progress, "done", BuildResponse.from(build));
            } catch (RuntimeException e) {
                ProblemDetail problem = ApiProblems.from(e);
                status = problem.getStatus();
                sendQuietly(progress, "error", problem);
            } finally {
                emitter.complete();
                log.atInfo().setMessage("http.build.generate.finished").addKeyValue("status", status).log();
            }
        });
        return emitter;
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable UUID id) {
        return getBuild.get(id)
                .<ResponseEntity<?>>map(b -> ResponseEntity.ok(BuildResponse.from(b)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Build não encontrada.")));
    }

    private static void sendQuietly(SseProgress progress, String event, Object data) {
        try {
            progress.send(event, data);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("http.build.generate.client_disconnected").log();
        }
    }
}
