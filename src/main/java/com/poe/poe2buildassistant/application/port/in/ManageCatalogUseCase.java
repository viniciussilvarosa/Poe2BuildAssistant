package com.poe.poe2buildassistant.application.port.in;

import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import java.util.Optional;

/** Curadoria do catálogo num rascunho, sem deploy. {@code adminUserId} vai só para a auditoria. */
public interface ManageCatalogUseCase {

    /** @throws com.poe.poe2buildassistant.domain.shared.DraftAlreadyExists já há um rascunho */
    CatalogVersion createDraft(String patch, String adminUserId);

    Optional<CatalogVersion> currentDraft();

    Catalog draftCatalog(long versionId);

    /**
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable versão não é rascunho
     * @throws com.poe.poe2buildassistant.domain.shared.InvalidCatalogChange deixaria o catálogo incoerente
     */
    void saveItem(long versionId, CatalogItem item, String adminUserId);

    void removeItem(long versionId, CatalogItem.Type type, String name, String adminUserId);
}
