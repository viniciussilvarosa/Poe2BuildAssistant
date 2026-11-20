package com.poe.poe2buildassistant.adapter.in.web.build;

import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest;
import java.util.Optional;

/** Corpo do POST /api/builds/generate. Seletores ausentes ou em branco = "Deixar a IA escolher". */
record GenerateBuildRequestDto(
        String concept, String characterClass, String ascendancy, String weaponType, String mainSkill, String playstyle) {

    static final int MAX_CONCEPT_LENGTH = 500;

    BuildRequest toDomain() {
        if (concept != null && concept.strip().length() > MAX_CONCEPT_LENGTH) {
            throw new InvalidBuildRequest("O conceito do personagem pode ter no máximo " + MAX_CONCEPT_LENGTH + " caracteres.");
        }
        return new BuildRequest(
                concept,
                new Constraints(
                        Optional.ofNullable(characterClass),
                        Optional.ofNullable(ascendancy),
                        Optional.ofNullable(weaponType),
                        Optional.ofNullable(mainSkill),
                        Optional.ofNullable(playstyle)));
    }
}
