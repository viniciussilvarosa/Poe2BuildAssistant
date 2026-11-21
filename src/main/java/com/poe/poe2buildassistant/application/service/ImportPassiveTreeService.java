package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.ImportPassiveTreeUseCase;
import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.application.port.out.PassiveTreeSourcePort;
import com.poe.poe2buildassistant.domain.catalog.CatalogNames;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound;
import com.poe.poe2buildassistant.domain.shared.PassiveTreeFormatChanged;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    /**
     * Sem transação aqui de propósito: o download de ~5 MB não pode segurar uma conexão do pool que a
     * geração também usa (chaos N1). A escrita abre a própria transação e reconfere o status.
     */
    @Override
    public int importInto(long versionId) {
        CatalogVersion version = catalogs.findVersion(versionId).orElseThrow(() -> new CatalogVersionNotFound(versionId));
        if (version.status() == CatalogVersion.Status.PUBLISHED) {
            throw new CatalogVersionNotEditable(version.patch());
        }
        try {
            List<PassiveNotable> notables = source.fetchNotables();
            requireStorableNames(notables);
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

    /** O banco guarda nomes de até 120 caracteres; um nome maior é sinal de formato novo (chaos N3). */
    private static void requireStorableNames(List<PassiveNotable> notables) {
        notables.stream()
                .filter(n -> n.name().length() > CatalogNames.MAX_LENGTH)
                .findFirst()
                .ifPresent(n -> {
                    throw new PassiveTreeFormatChanged(
                            "o notable \"" + n.name().substring(0, 40) + "…\" tem mais de " + CatalogNames.MAX_LENGTH + " caracteres");
                });
    }
}
