package com.poe.poe2buildassistant.application.port.in;

import java.util.List;

/** Opções do formulário de build, lidas da versão publicada do catálogo. */
public interface GetCatalogOptionsUseCase {

    /** Leitura pronta para o formulário: tudo em ordem alfabética, ascendências sob a própria classe. */
    record CatalogOptions(
            String patch,
            List<ClassOption> classes,
            List<String> weaponTypes,
            List<SkillGemOption> skillGems,
            List<String> playstyles) {

        public CatalogOptions {
            classes = List.copyOf(classes);
            weaponTypes = List.copyOf(weaponTypes);
            skillGems = List.copyOf(skillGems);
            playstyles = List.copyOf(playstyles);
        }
    }

    record ClassOption(String name, List<String> ascendancies) {
        public ClassOption {
            ascendancies = List.copyOf(ascendancies);
        }
    }

    /** Lista de armas vazia = a gema não exige arma (ex.: a maioria dos feitiços). */
    record SkillGemOption(String name, List<String> weaponTypes) {
        public SkillGemOption {
            weaponTypes = List.copyOf(weaponTypes);
        }
    }

    /** @throws com.poe.poe2buildassistant.domain.shared.CatalogNotPublished se nenhuma versão foi publicada */
    CatalogOptions get();
}
