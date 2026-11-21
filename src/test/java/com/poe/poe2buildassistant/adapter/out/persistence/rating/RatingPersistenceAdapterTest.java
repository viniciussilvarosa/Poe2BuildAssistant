package com.poe.poe2buildassistant.adapter.out.persistence.rating;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, RatingPersistenceAdapter.class})
class RatingPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Autowired private RatingPersistenceAdapter adapter;
    @Autowired private JdbcClient jdbc;
    private UUID build;

    /** Uma build qualquer, ligada ao catálogo publicado do seed. */
    @BeforeEach
    void build() {
        build = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO build (id, owner_id, catalog_version_id, content, created_at)
                        SELECT ?, NULL, id, '{}'::jsonb, now() FROM catalog_version WHERE status = 'PUBLISHED'""")
                .param(build)
                .update();
    }

    private String valueOf(String requesterKey) {
        return jdbc.sql("SELECT value FROM build_rating WHERE build_id = ? AND requester_key = ?")
                .params(build, requesterKey)
                .query(String.class)
                .single();
    }

    @Test
    @DisplayName("segunda avaliação do mesmo avaliador substitui a primeira")
    void substitui() {
        adapter.upsert(new BuildRating(build, "anon:" + "a".repeat(64), BuildRating.Value.UP, NOW));
        adapter.upsert(new BuildRating(build, "anon:" + "a".repeat(64), BuildRating.Value.DOWN, NOW.plusSeconds(5)));

        assertThat(jdbc.sql("SELECT count(*) FROM build_rating WHERE build_id = ?").param(build).query(Integer.class).single())
                .isEqualTo(1);
        assertThat(valueOf("anon:" + "a".repeat(64))).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("calcula taxa positiva = UP / total")
    void taxaPositiva() {
        jdbc.sql("DELETE FROM build_rating").update();
        for (int i = 0; i < 10; i++) {
            adapter.upsert(new BuildRating(build, "user:" + i, i < 7 ? BuildRating.Value.UP : BuildRating.Value.DOWN, NOW));
        }

        assertThat(adapter.positiveRate()).hasValue(0.70);
    }

    @Test
    @DisplayName("sem avaliações a taxa fica vazia, não zero")
    void semAvaliacoes() {
        jdbc.sql("DELETE FROM build_rating").update();

        assertThat(adapter.positiveRate()).isEmpty();
    }
}
