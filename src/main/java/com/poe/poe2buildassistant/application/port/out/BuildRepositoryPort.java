package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.build.Build;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuildRepositoryPort {

    Build save(Build build);

    Optional<Build> findById(UUID id);

    /** Histórico do usuário, do mais recente para o mais antigo. Builds sem dono nunca aparecem. */
    List<Build> findByOwner(String ownerId);
}
