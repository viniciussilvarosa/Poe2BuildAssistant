package com.poe.poe2buildassistant.adapter.in.web.build;

import com.poe.poe2buildassistant.adapter.in.web.RequesterResolver;
import com.poe.poe2buildassistant.application.port.in.RateBuildUseCase;
import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 👍/👎 de qualquer build, de visitante ou logado: toda build é salva. */
@RestController
class RateBuildController {

    /** value como texto: um valor fora de UP/DOWN vira 400 com mensagem própria, não erro de JSON. */
    record RatingBody(String value) {}

    private final RateBuildUseCase rateBuild;
    private final RequesterResolver requesters;

    RateBuildController(RateBuildUseCase rateBuild, RequesterResolver requesters) {
        this.rateBuild = rateBuild;
        this.requesters = requesters;
    }

    record RatingView(BuildRating.Value value) {}

    /** 204 quando este avaliador ainda não votou (ou a build não existe): nada a marcar na tela. */
    @GetMapping("/api/builds/{id}/rating")
    ResponseEntity<RatingView> current(@PathVariable UUID id, HttpServletRequest http) {
        return rateBuild.currentRating(id, requesters.resolve(http))
                .map(v -> ResponseEntity.ok(new RatingView(v)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/builds/{id}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@PathVariable UUID id, @RequestBody RatingBody body, HttpServletRequest http) {
        rateBuild.rate(id, requesters.resolve(http), BuildRating.Value.parse(body.value()));
    }
}
