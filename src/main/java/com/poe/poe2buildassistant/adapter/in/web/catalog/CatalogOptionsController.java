package com.poe.poe2buildassistant.adapter.in.web.catalog;

import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase;
import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Opções do formulário de build. Público: o login é opcional no produto. */
@RestController
@RequestMapping("/api/catalog")
class CatalogOptionsController {

    /**
     * O catálogo só muda quando o admin publica um patch novo. Cache só no navegador: a resposta
     * leva o Set-Cookie do CSRF, que um proxy com "public" repassaria a todos (chaos O2).
     */
    private static final Duration MAX_AGE = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(CatalogOptionsController.class);

    private final GetCatalogOptionsUseCase getOptions;

    CatalogOptionsController(GetCatalogOptionsUseCase getOptions) {
        this.getOptions = getOptions;
    }

    @GetMapping("/options")
    ResponseEntity<CatalogOptionsResponse> options() {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(MAX_AGE).cachePrivate())
                    .body(CatalogOptionsResponse.from(getOptions.get()));
        } catch (CatalogNotPublished e) {
            // O ApiExceptionHandler transforma em 503 ProblemDetail em pt-BR.
            log.atWarn().setMessage("catalog.options.unavailable").log();
            throw e;
        }
    }
}
