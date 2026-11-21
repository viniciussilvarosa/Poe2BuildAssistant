package com.poe.poe2buildassistant.adapter.out.persistence.rating;

import com.poe.poe2buildassistant.application.port.out.RatingRepositoryPort;
import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RatingPersistenceAdapter implements RatingRepositoryPort {

    private final JdbcClient jdbc;

    RatingPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void upsert(BuildRating rating) {
        jdbc.sql("""
                        INSERT INTO build_rating (build_id, requester_key, value, rated_at) VALUES (?, ?, ?, ?)
                        ON CONFLICT (build_id, requester_key) DO UPDATE SET value = EXCLUDED.value, rated_at = EXCLUDED.rated_at""")
                .params(rating.buildId(), rating.requesterKey(), rating.value().name(), Timestamp.from(rating.ratedAt()))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BuildRating.Value> findValue(UUID buildId, String requesterKey) {
        return jdbc.sql("SELECT value FROM build_rating WHERE build_id = ? AND requester_key = ?")
                .params(buildId, requesterKey)
                .query(String.class)
                .optional()
                .map(BuildRating.Value::valueOf);
    }

    /** A mesma consulta documentada na V9 para acompanhar o critério de sucesso (≥ 70%). */
    @Override
    @Transactional(readOnly = true)
    public OptionalDouble positiveRate() {
        // avg() de zero linhas é NULL: sem avaliações ainda, a taxa fica vazia, não 0%.
        return jdbc.sql("SELECT avg(CASE value WHEN 'UP' THEN 1.0 ELSE 0.0 END) AS rate FROM build_rating")
                .query((rs, n) -> {
                    double rate = rs.getDouble("rate");
                    return rs.wasNull() ? OptionalDouble.empty() : OptionalDouble.of(rate);
                })
                .single();
    }
}
