package com.poe.poe2buildassistant.domain.quota;

import java.time.Instant;
import java.util.Optional;

/** Resultado da política de cota: permitido, ou bloqueado até {@code retryAt}. */
public record QuotaDecision(boolean allowed, Optional<Instant> retryAt) {

    public static QuotaDecision allow() {
        return new QuotaDecision(true, Optional.empty());
    }

    public static QuotaDecision block(Instant retryAt) {
        return new QuotaDecision(false, Optional.of(retryAt));
    }
}
