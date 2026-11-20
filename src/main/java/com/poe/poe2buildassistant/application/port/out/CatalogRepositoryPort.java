package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.catalog.Catalog;

/** Acesso ao catálogo versionado. Operações de rascunho entram com a API de administração (#17). */
public interface CatalogRepositoryPort {

    /**
     * Carrega a versão publicada completa.
     *
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogNotPublished se nenhuma versão foi publicada
     */
    Catalog loadPublished();
}
