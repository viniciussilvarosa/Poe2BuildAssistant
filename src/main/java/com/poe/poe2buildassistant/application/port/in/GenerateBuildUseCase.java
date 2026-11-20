package com.poe.poe2buildassistant.application.port.in;

import com.poe.poe2buildassistant.application.port.out.GenerationProgressPort;
import com.poe.poe2buildassistant.domain.build.Build;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.quota.Requester;
import java.util.Objects;

/** Gera, valida e salva uma build a partir do pedido do usuário. */
public interface GenerateBuildUseCase {

    record GenerateBuildCommand(BuildRequest request, Requester requester) {
        public GenerateBuildCommand {
            Objects.requireNonNull(request);
            Objects.requireNonNull(requester);
        }
    }

    /**
     * @param progress recebe as etapas desta requisição (cada requisição tem o seu)
     * @throws com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest pedido incoerente com o catálogo
     * @throws com.poe.poe2buildassistant.domain.shared.QuotaExceeded limite diário atingido
     * @throws com.poe.poe2buildassistant.domain.shared.ProviderUnavailable IA indisponível (cota devolvida)
     * @throws com.poe.poe2buildassistant.domain.shared.GenerationFailed nenhuma tentativa produziu build válida
     */
    Build generate(GenerateBuildCommand command, GenerationProgressPort progress);
}
