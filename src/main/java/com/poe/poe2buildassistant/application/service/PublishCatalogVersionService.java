package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.PublishCatalogVersionUseCase;
import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.domain.build.BuildValidator;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.shared.InvalidCatalogChange;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishCatalogVersionService implements PublishCatalogVersionUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishCatalogVersionService.class);

    private final CatalogEditorPort catalogs;

    public PublishCatalogVersionService(CatalogEditorPort catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    @Transactional
    public CatalogVersion publish(long draftId, String adminUserId) {
        Catalog catalog = catalogs.loadVersion(draftId);
        requireUsable(catalog);
        CatalogVersion published = catalogs.publish(draftId);
        log.atInfo()
                .setMessage("catalog.published")
                .addKeyValue("versionId", draftId)
                .addKeyValue("patch", published.patch())
                .addKeyValue("adminUserId", adminUserId)
                .addKeyValue("classes", catalog.classes().size())
                .addKeyValue("ascendancies", catalog.ascendancies().size())
                .addKeyValue("skillGems", catalog.skillGems().size())
                .addKeyValue("supportGems", catalog.supportGems().size())
                .addKeyValue("notables", catalog.notables().size())
                .log();
        return published;
    }

    /**
     * Publicar é imediato: um catálogo com que nenhuma build passa no validador derrubaria a geração
     * para todos até a próxima publicação (chaos C3).
     */
    private static void requireUsable(Catalog c) {
        List<String> missing = new ArrayList<>();
        if (c.classes().stream().noneMatch(cl -> !c.ascendanciesOf(cl).isEmpty())) missing.add("uma classe com ascendência");
        if (c.weaponTypes().isEmpty()) missing.add("um tipo de arma");
        if (c.skillGems().isEmpty()) missing.add("uma gema de habilidade");
        if (c.supportGems().size() < BuildValidator.MIN_SUPPORTS) missing.add("uma gema de suporte");
        if (c.notables().size() < BuildValidator.MIN_NOTABLES) missing.add(BuildValidator.MIN_NOTABLES + " notables");
        if (!missing.isEmpty()) {
            throw new InvalidCatalogChange("para publicar, o rascunho precisa de pelo menos " + String.join(", ", missing));
        }
    }
}
