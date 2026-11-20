package com.poe.poe2buildassistant.domain.shared;

import java.time.Instant;
import java.util.Objects;

/** O limite diário de gerações foi atingido; libera em {@link #retryAt()}. */
public final class QuotaExceeded extends DomainException {

    private final Instant retryAt;

    public QuotaExceeded(Instant retryAt) {
        super("Você atingiu o limite diário de gerações de build.");
        this.retryAt = Objects.requireNonNull(retryAt);
    }

    public Instant retryAt() {
        return retryAt;
    }
}
