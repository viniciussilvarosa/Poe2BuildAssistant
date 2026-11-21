package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.ListMyBuildsUseCase;
import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.domain.build.Build;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListMyBuildsService implements ListMyBuildsUseCase {

    private final BuildRepositoryPort builds;

    public ListMyBuildsService(BuildRepositoryPort builds) {
        this.builds = builds;
    }

    @Override
    public List<Build> list(String userId) {
        return builds.findByOwner(userId);
    }
}
