package com.poe.poe2buildassistant.domain.shared;

import java.time.Instant;
import java.util.Optional;

/** O provedor de IA não atendeu (cota esgotada, fora do ar ou lento demais). */
public final class ProviderUnavailable extends DomainException {

    private final Instant retryAt;

    public ProviderUnavailable(Optional<Instant> retryAt) {
        super("O serviço de IA está indisponível no momento. Tente novamente mais tarde.");
        this.retryAt = retryAt.orElse(null);
    }

    /** Quando o provedor indicou que volta a aceitar pedidos, se informado. */
    public Optional<Instant> retryAt() {
        return Optional.ofNullable(retryAt);
    }
}
