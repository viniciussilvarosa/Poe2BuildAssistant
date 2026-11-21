package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

public interface RatingRepositoryPort {

    /** Grava ou substitui a avaliação do avaliador nesta build. */
    void upsert(BuildRating rating);

    Optional<BuildRating.Value> findValue(UUID buildId, String requesterKey);

    /** Fração de UP entre todas as avaliações; vazio quando ainda não há nenhuma. */
    OptionalDouble positiveRate();
}
