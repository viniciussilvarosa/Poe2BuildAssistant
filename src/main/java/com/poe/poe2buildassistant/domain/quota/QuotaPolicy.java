package com.poe.poe2buildassistant.domain.quota;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Limites diários de geração, que repartem a cota gratuita do provedor de IA. O dia é contado em
 * UTC e reinicia à meia-noite UTC. Os valores são provisórios até medir a cota real.
 */
public record QuotaPolicy(int anonymousDaily, int authenticatedDaily) {

    public QuotaPolicy {
        if (anonymousDaily < 0 || authenticatedDaily < 0) {
            throw new IllegalArgumentException("limite de cota negativo");
        }
    }

    public int limitFor(Requester requester) {
        return switch (requester) {
            case Requester.Anonymous a -> anonymousDaily;
            case Requester.Authenticated u -> authenticatedDaily;
        };
    }

    /** @param usedToday gerações já consumidas hoje */
    public QuotaDecision decide(Requester requester, int usedToday, Instant now) {
        return usedToday < limitFor(requester) ? QuotaDecision.allow() : QuotaDecision.block(nextReset(now));
    }

    public LocalDate dayOf(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC);
    }

    public Instant nextReset(Instant now) {
        return dayOf(now).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
