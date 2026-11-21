package com.poe.poe2buildassistant.application.port.in;

import com.poe.poe2buildassistant.domain.feedback.BuildRating;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.util.Optional;
import java.util.UUID;

/** 👍/👎 numa build. Votar de novo substitui o voto anterior do mesmo avaliador. */
public interface RateBuildUseCase {

    /** @throws com.poe.poe2buildassistant.domain.shared.BuildNotFound build inexistente */
    void rate(UUID buildId, Requester requester, BuildRating.Value value);

    /** O voto deste avaliador nesta build, para a tela marcar o botão; nunca o de outros (#50). */
    Optional<BuildRating.Value> currentRating(UUID buildId, Requester requester);
}
