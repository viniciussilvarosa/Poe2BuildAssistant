package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import java.util.OptionalDouble;

public interface RatingRepositoryPort {

    /** Grava ou substitui a avaliação do avaliador nesta build. */
    void upsert(BuildRating rating);

    /** Fração de UP entre todas as avaliações; vazio quando ainda não há nenhuma. */
    OptionalDouble positiveRate();
}
