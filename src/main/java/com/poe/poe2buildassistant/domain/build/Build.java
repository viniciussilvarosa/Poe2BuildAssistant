package com.poe.poe2buildassistant.domain.build;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Build gerada e já aprovada pelo {@link BuildValidator}. Toda build é salva; a de visitante fica
 * sem dono e nunca aparece em histórico (ADR-002 § 4).
 *
 * @param catalogVersionId versão do catálogo contra a qual a build foi validada
 */
public record Build(
        UUID id,
        Optional<String> ownerId,
        long catalogVersionId,
        BuildRequest request,
        GeneratedBuildDraft content,
        Instant createdAt) {

    public Build {
        Objects.requireNonNull(id, "build sem id");
        Objects.requireNonNull(ownerId, "dono deve ser Optional");
        Objects.requireNonNull(request, "build sem pedido");
        Objects.requireNonNull(content, "build sem conteúdo");
        Objects.requireNonNull(createdAt, "build sem data");
    }
}
