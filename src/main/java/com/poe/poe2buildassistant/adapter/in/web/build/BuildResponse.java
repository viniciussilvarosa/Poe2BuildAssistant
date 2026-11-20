package com.poe.poe2buildassistant.adapter.in.web.build;

import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import java.time.Instant;
import java.util.UUID;

/**
 * Build devolvida pela API (evento done e GET /api/builds/{id}). O dono nunca é exposto: o link
 * da build funciona como link compartilhável, protegido pelo UUID não adivinhável.
 */
record BuildResponse(UUID id, Instant createdAt, RequestView request, GeneratedBuildDraft build) {

    record RequestView(
            String concept, String characterClass, String ascendancy, String weaponType, String mainSkill, String playstyle) {}

    static BuildResponse from(Build b) {
        Constraints c = b.request().constraints();
        return new BuildResponse(
                b.id(),
                b.createdAt(),
                new RequestView(
                        b.request().concept(),
                        c.characterClass().orElse(null),
                        c.ascendancy().orElse(null),
                        c.weaponType().orElse(null),
                        c.mainSkill().orElse(null),
                        c.playstyle().orElse(null)),
                b.content());
    }
}
