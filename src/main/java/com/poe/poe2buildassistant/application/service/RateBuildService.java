package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.RateBuildUseCase;
import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.application.port.out.ClockPort;
import com.poe.poe2buildassistant.application.port.out.RatingRepositoryPort;
import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import com.poe.poe2buildassistant.domain.quota.Requester;
import com.poe.poe2buildassistant.domain.shared.BuildNotFound;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateBuildService implements RateBuildUseCase {

    private static final Logger log = LoggerFactory.getLogger(RateBuildService.class);

    private final BuildRepositoryPort builds;
    private final RatingRepositoryPort ratings;
    private final ClockPort clock;

    public RateBuildService(BuildRepositoryPort builds, RatingRepositoryPort ratings, ClockPort clock) {
        this.builds = builds;
        this.ratings = ratings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void rate(UUID buildId, Requester requester, BuildRating.Value value) {
        if (builds.findById(buildId).isEmpty()) {
            throw new BuildNotFound(buildId);
        }
        ratings.upsert(new BuildRating(buildId, requester.key(), value, clock.now()));
        log.atInfo()
                .setMessage("build.rated")
                .addKeyValue("value", value)
                .addKeyValue("requesterType", requester.type())
                .log();
    }
}
