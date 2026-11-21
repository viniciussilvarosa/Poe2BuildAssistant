package com.poe.poe2buildassistant.domain.shared;

import java.util.UUID;

public final class BuildNotFound extends DomainException {

    public BuildNotFound(UUID id) {
        super("Build não encontrada.");
    }
}
