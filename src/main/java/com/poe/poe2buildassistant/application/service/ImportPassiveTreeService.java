package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.ImportPassiveTreeUseCase;
import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.application.port.out.PassiveTreeSourcePort;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disparado pelo admin, nunca por geração: confere a versão antes de baixar o export, para não
 * gastar o repositório oficial à toa.
 */
@Service
public class ImportPassiveTreeService implements ImportPassiveTreeUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportPassiveTreeService.class);

    private final CatalogEditorPort catalogs;
    private final PassiveTreeSourcePort source;

    public ImportPassiveTreeService(CatalogEditorPort catalogs, PassiveTreeSourcePort source) {
        this.catalogs = catalogs;
        this.source = source;
    }

    @Override
    @Transactional
    public int importInto(long versionId) {
        CatalogVersion version = catalogs.findVersion(versionId).orElseThrow(() -> new CatalogVersionNotFound(versionId));
        if (version.status() == CatalogVersion.Status.PUBLISHED) {
            throw new CatalogVersionNotEditable(version.patch());
        }
        try {
            List<PassiveNotable> notables = source.fetchNotables();
            catalogs.replaceNotables(versionId, notables);
            log.atInfo()
                    .setMessage("passive_tree.import.completed")
                    .addKeyValue("versionId", versionId)
                    .addKeyValue("notables", notables.size())
                    .log();
            return notables.size();
        } catch (RuntimeException e) {
            log.atError()
                    .setMessage("passive_tree.import.failed")
                    .addKeyValue("versionId", versionId)
                    .addKeyValue("cause", e.getMessage())
                    .log();
            throw e;
        }
    }
}
