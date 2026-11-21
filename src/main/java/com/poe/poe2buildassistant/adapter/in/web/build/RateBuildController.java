package com.poe.poe2buildassistant.adapter.in.web.build;

import com.poe.poe2buildassistant.adapter.in.web.RequesterResolver;
import com.poe.poe2buildassistant.application.port.in.RateBuildUseCase;
import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/api/builds/{id}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@PathVariable UUID id, @RequestBody RatingBody body, HttpServletRequest http) {
        rateBuild.rate(id, requesters.resolve(http), BuildRating.Value.parse(body.value()));
    }
}
