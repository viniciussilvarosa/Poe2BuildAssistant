package com.poe.poe2buildassistant.adapter.in.web;

import com.poe.poe2buildassistant.domain.shared.CatalogNotPublished;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound;
import com.poe.poe2buildassistant.domain.shared.DraftAlreadyExists;
import com.poe.poe2buildassistant.domain.shared.GenerationFailed;
import com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest;
import com.poe.poe2buildassistant.domain.shared.InvalidCatalogChange;
import com.poe.poe2buildassistant.domain.shared.PassiveTreeFormatChanged;
import com.poe.poe2buildassistant.domain.shared.PassiveTreeUnavailable;
import com.poe.poe2buildassistant.domain.shared.ProviderUnavailable;
import com.poe.poe2buildassistant.domain.shared.QuotaExceeded;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Tradução única das exceções de domínio para ProblemDetail (RFC 9457), em pt-BR. */
public final class ApiProblems {

    private static final Logger log = LoggerFactory.getLogger(ApiProblems.class);

    private ApiProblems() {}

    public static ProblemDetail from(Throwable e) {
        return switch (e) {
            case InvalidBuildRequest i -> problem(HttpStatus.BAD_REQUEST, "Pedido inválido", i.getMessage(), null);
            case QuotaExceeded q -> problem(HttpStatus.TOO_MANY_REQUESTS, "Limite diário atingido", q.getMessage(), q.retryAt());
            case ProviderUnavailable p ->
                    problem(HttpStatus.SERVICE_UNAVAILABLE, "IA indisponível", p.getMessage(), p.retryAt().orElse(null));
            case CatalogNotPublished c -> problem(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo indisponível", c.getMessage(), null);
            case GenerationFailed g -> problem(HttpStatus.BAD_GATEWAY, "Geração falhou", g.getMessage(), null);
            case CatalogVersionNotFound n -> problem(HttpStatus.NOT_FOUND, "Versão do catálogo não encontrada", n.getMessage(), null);
            case CatalogVersionNotEditable n -> problem(HttpStatus.CONFLICT, "Versão publicada", n.getMessage(), null);
            case DraftAlreadyExists d -> problem(HttpStatus.CONFLICT, "Rascunho já existe", d.getMessage(), null);
            case InvalidCatalogChange i -> problem(HttpStatus.BAD_REQUEST, "Mudança no catálogo recusada", i.getMessage(), null);
            case PassiveTreeFormatChanged f -> problem(HttpStatus.BAD_GATEWAY, "Export da árvore mudou", f.getMessage(), null);
            case PassiveTreeUnavailable u -> problem(HttpStatus.SERVICE_UNAVAILABLE, "Export da árvore indisponível", u.getMessage(), null);
            default -> {
                log.atError().setMessage("http.unexpected_error").setCause(e).log();
                yield problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado", "Algo deu errado. Tente novamente.", null);
            }
        };
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, Instant retryAt) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (retryAt != null) {
            problem.setProperty("retryAt", retryAt.toString());
        }
        return problem;
    }
}
