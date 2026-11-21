package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.ManageCatalogUseCase;
import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.catalog.CatalogNames;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound;
import com.poe.poe2buildassistant.domain.shared.DraftAlreadyExists;
import com.poe.poe2buildassistant.domain.shared.InvalidCatalogChange;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada mudança é aplicada primeiro no catálogo do domínio, que reconfere as invariantes (ascendência
 * com classe, gema com armas existentes, nomes únicos); só depois vai para o banco.
 */
@Service
public class ManageCatalogService implements ManageCatalogUseCase {

    private static final Logger log = LoggerFactory.getLogger(ManageCatalogService.class);

    private final CatalogEditorPort catalogs;

    public ManageCatalogService(CatalogEditorPort catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    @Transactional
    public CatalogVersion createDraft(String patch, String adminUserId) {
        String name = check(() -> CatalogNames.requireName(patch, "versão do catálogo"));
        if (name.length() > CatalogVersion.MAX_PATCH_LENGTH) {
            throw new InvalidCatalogChange("o nome da versão passa de " + CatalogVersion.MAX_PATCH_LENGTH + " caracteres");
        }
        catalogs.findDraft().ifPresent(d -> {
            throw new DraftAlreadyExists(d.patch());
        });
        CatalogVersion draft = catalogs.createDraftFromPublished(name);
        log.atInfo()
                .setMessage("catalog.draft.created")
                .addKeyValue("versionId", draft.id())
                .addKeyValue("patch", draft.patch())
                .addKeyValue("adminUserId", adminUserId)
                .log();
        return draft;
    }

    @Override
    public Optional<CatalogVersion> currentDraft() {
        return catalogs.findDraft();
    }

    @Override
    public Catalog draftCatalog(long versionId) {
        return catalogs.loadVersion(versionId);
    }

    @Override
    @Transactional
    public void saveItem(long versionId, CatalogItem item, String adminUserId) {
        edit(versionId, c -> c.with(item));
        catalogs.saveItem(versionId, item);
        logChange(item.type(), "saved", adminUserId);
    }

    @Override
    @Transactional
    public void removeItem(long versionId, CatalogItem.Type type, String name, String adminUserId) {
        edit(versionId, c -> c.without(type, name));
        catalogs.removeItem(versionId, type, name);
        logChange(type, "removed", adminUserId);
    }

    /** Confere que é rascunho e que a mudança mantém o catálogo coerente, sem gravar nada. */
    private void edit(long versionId, UnaryOperator<Catalog> change) {
        CatalogVersion version = catalogs.findVersion(versionId).orElseThrow(() -> new CatalogVersionNotFound(versionId));
        if (version.status() != CatalogVersion.Status.DRAFT) {
            throw new CatalogVersionNotEditable(version.patch());
        }
        Catalog current = catalogs.loadVersion(versionId);
        check(() -> change.apply(current));
    }

    private static <T> T check(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new InvalidCatalogChange(e.getMessage());
        }
    }

    private static void logChange(CatalogItem.Type type, String action, String adminUserId) {
        log.atInfo()
                .setMessage("catalog.item.changed")
                .addKeyValue("type", type)
                .addKeyValue("action", action)
                .addKeyValue("adminUserId", adminUserId)
                .log();
    }
}
