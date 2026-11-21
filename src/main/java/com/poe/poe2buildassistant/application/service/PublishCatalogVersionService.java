package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.PublishCatalogVersionUseCase;
import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
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
        CatalogVersion published = catalogs.publish(draftId);
        Catalog catalog = catalogs.loadVersion(draftId);
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
}
