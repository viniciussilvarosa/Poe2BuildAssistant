package com.poe.poe2buildassistant.adapter.in.web.build;

import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.shared.InvalidBuildRequest;
import java.util.Optional;

/** Corpo do POST /api/builds/generate. Seletores ausentes ou em branco = "Deixar a IA escolher". */
record GenerateBuildRequestDto(
        String concept, String characterClass, String ascendancy, String weaponType, String mainSkill, String playstyle) {

    static final int MAX_CONCEPT_LENGTH = 500;
    /** Mesmo tamanho máximo de um nome no catálogo (VARCHAR(120)). */
    static final int MAX_SELECTOR_LENGTH = 120;

    BuildRequest toDomain() {
        if (concept != null && concept.strip().length() > MAX_CONCEPT_LENGTH) {
            throw new InvalidBuildRequest("O conceito do personagem pode ter no máximo " + MAX_CONCEPT_LENGTH + " caracteres.");
        }
        // Recusa antes do stream e sem ecoar o valor (chaos X2).
        requireShort("Classe", characterClass);
        requireShort("Ascendência", ascendancy);
        requireShort("Arma", weaponType);
        requireShort("Habilidade principal", mainSkill);
        requireShort("Estilo de jogo", playstyle);
        return new BuildRequest(
                concept,
                new Constraints(
                        Optional.ofNullable(characterClass),
                        Optional.ofNullable(ascendancy),
                        Optional.ofNullable(weaponType),
                        Optional.ofNullable(mainSkill),
                        Optional.ofNullable(playstyle)));
    }

    private static void requireShort(String label, String value) {
        if (value != null && value.length() > MAX_SELECTOR_LENGTH) {
            throw new InvalidBuildRequest("O campo " + label + " é longo demais para ser uma opção do catálogo.");
        }
    }
}
