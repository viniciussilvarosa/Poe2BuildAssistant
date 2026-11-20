package com.poe.poe2buildassistant.adapter.out.persistence.build;

import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import java.util.Optional;

/**
 * Formato do jsonb de uma build. O campo {@code schema} permite evoluir o formato sem migrar
 * linhas antigas. Optional vira null aqui, para o JSON ficar simples e legível em SQL.
 */
record BuildDocument(int schema, RequestDocument request, GeneratedBuildDraft draft) {

    static final int CURRENT_SCHEMA = 1;

    record RequestDocument(
            String concept, String characterClass, String ascendancy, String weaponType, String mainSkill, String playstyle) {}

    static BuildDocument from(BuildRequest request, GeneratedBuildDraft draft) {
        Constraints c = request.constraints();
        return new BuildDocument(
                CURRENT_SCHEMA,
                new RequestDocument(
                        request.concept(),
                        c.characterClass().orElse(null),
                        c.ascendancy().orElse(null),
                        c.weaponType().orElse(null),
                        c.mainSkill().orElse(null),
                        c.playstyle().orElse(null)),
                draft);
    }

    BuildRequest toRequest() {
        return new BuildRequest(
                request.concept(),
                new Constraints(
                        Optional.ofNullable(request.characterClass()),
                        Optional.ofNullable(request.ascendancy()),
                        Optional.ofNullable(request.weaponType()),
                        Optional.ofNullable(request.mainSkill()),
                        Optional.ofNullable(request.playstyle())));
    }
}
