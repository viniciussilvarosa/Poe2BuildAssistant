package com.poe.poe2buildassistant.application.port.in;

import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;

/** Publica o rascunho: os seletores e o validador passam a usá-lo na hora; a anterior é arquivada. */
public interface PublishCatalogVersionUseCase {

    CatalogVersion publish(long draftId, String adminUserId);
}
