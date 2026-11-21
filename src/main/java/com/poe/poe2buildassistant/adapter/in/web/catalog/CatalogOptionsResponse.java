package com.poe.poe2buildassistant.adapter.in.web.catalog;

import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase.CatalogOptions;
import java.util.List;

/**
 * Contrato de {@code GET /api/catalog/options}, consumido pelo formulário (#12). Cada opção é um
 * objeto com {@code name}, para crescer (ex.: descrição) sem quebrar o front.
 */
record CatalogOptionsResponse(
        String patch,
        List<ClassDto> classes,
        List<NamedDto> weaponTypes,
        List<SkillGemDto> skillGems,
        List<NamedDto> playstyles) {

    record NamedDto(String name) {}

    record ClassDto(String name, List<NamedDto> ascendancies) {}

    /** {@code weaponTypes} vazio = a gema não exige arma. */
    record SkillGemDto(String name, List<String> weaponTypes) {}

    static CatalogOptionsResponse from(CatalogOptions options) {
        return new CatalogOptionsResponse(
                options.patch(),
                options.classes().stream()
                        .map(c -> new ClassDto(c.name(), named(c.ascendancies())))
                        .toList(),
                named(options.weaponTypes()),
                options.skillGems().stream().map(g -> new SkillGemDto(g.name(), g.weaponTypes())).toList(),
                named(options.playstyles()));
    }

    private static List<NamedDto> named(List<String> names) {
        return names.stream().map(NamedDto::new).toList();
    }
}
