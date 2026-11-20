package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.GetBuildUseCase;
import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.domain.build.Build;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetBuildService implements GetBuildUseCase {

    private final BuildRepositoryPort builds;

    public GetBuildService(BuildRepositoryPort builds) {
        this.builds = builds;
    }

    @Override
    public Optional<Build> get(UUID id) {
        return builds.findById(id);
    }
}
