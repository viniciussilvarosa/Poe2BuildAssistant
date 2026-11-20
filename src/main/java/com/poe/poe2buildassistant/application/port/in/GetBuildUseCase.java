package com.poe.poe2buildassistant.application.port.in;

import com.poe.poe2buildassistant.domain.build.Build;
import java.util.Optional;
import java.util.UUID;

/** Leitura de uma build salva pelo id (link da tela de resultado). */
public interface GetBuildUseCase {

    Optional<Build> get(UUID id);
}
