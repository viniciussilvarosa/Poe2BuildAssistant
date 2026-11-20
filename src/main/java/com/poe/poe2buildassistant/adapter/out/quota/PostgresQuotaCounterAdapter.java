package com.poe.poe2buildassistant.adapter.out.quota;

import com.poe.poe2buildassistant.application.port.out.QuotaCounterPort;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Contadores diários no PostgreSQL. O consumo é um único UPSERT condicional: a linha fica travada
 * durante o update, então verificação e incremento são atômicos mesmo sob concorrência.
 */
@Component
class PostgresQuotaCounterAdapter implements QuotaCounterPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresQuotaCounterAdapter.class);

    private final JdbcClient jdbc;

    PostgresQuotaCounterAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryConsume(Requester requester, LocalDate day, int limit) {
        boolean consumed = limit > 0
                && jdbc.sql("""
                                INSERT INTO quota_counter (requester_key, day, count) VALUES (:key, :day, 1)
                                ON CONFLICT (requester_key, day)
                                    DO UPDATE SET count = quota_counter.count + 1
                                    WHERE quota_counter.count < :limit
                                RETURNING count
                                """)
                        .param("key", requester.key())
                        .param("day", day)
                        .param("limit", limit)
                        .query(Integer.class)
                        .optional()
                        .isPresent();
        if (!consumed) {
            log.atInfo().setMessage("quota.blocked").addKeyValue("requesterType", requester.type()).log();
        }
        return consumed;
    }

    @Override
    public void release(Requester requester, LocalDate day) {
        jdbc.sql("UPDATE quota_counter SET count = count - 1 WHERE requester_key = :key AND day = :day AND count > 0")
                .param("key", requester.key())
                .param("day", day)
                .update();
    }

    @Override
    public int current(Requester requester, LocalDate day) {
        return jdbc.sql("SELECT count FROM quota_counter WHERE requester_key = :key AND day = :day")
                .param("key", requester.key())
                .param("day", day)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }
}
