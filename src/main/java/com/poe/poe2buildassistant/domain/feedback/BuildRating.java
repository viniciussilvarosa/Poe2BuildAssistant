package com.poe.poe2buildassistant.domain.feedback;

import com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Avaliação de uma build por um avaliador (a mesma chave da cota: hash do IP ou id do usuário).
 * Uma por avaliador e build; a métrica de sucesso é a fração de UP.
 */
public record BuildRating(UUID buildId, String requesterKey, Value value, Instant ratedAt) {

    public enum Value {
        UP,
        DOWN;

        /** @throws InvalidBuildRequest para qualquer coisa que não seja UP ou DOWN */
        public static Value parse(String raw) {
            for (Value v : values()) {
                if (v.name().equals(raw)) {
                    return v;
                }
            }
            throw new InvalidBuildRequest("A avaliação precisa ser UP ou DOWN.");
        }
    }

    public BuildRating {
        Objects.requireNonNull(buildId, "avaliação sem build");
        Objects.requireNonNull(requesterKey, "avaliação sem avaliador");
        Objects.requireNonNull(value, "avaliação sem valor");
        Objects.requireNonNull(ratedAt, "avaliação sem data");
    }
}
