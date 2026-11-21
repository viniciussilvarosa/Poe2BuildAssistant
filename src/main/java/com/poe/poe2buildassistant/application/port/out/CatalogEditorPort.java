package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import java.util.List;
import java.util.Optional;

/**
 * Escrita no catálogo versionado. Separada de {@link CatalogRepositoryPort}, que é só leitura e
 * usada a cada geração; a API de administração (#17) acrescenta as demais operações aqui.
 */
public interface CatalogEditorPort {

    Optional<CatalogVersion> findVersion(long versionId);

    /** Apaga os notables da versão e grava estes no lugar, numa transação. */
    void replaceNotables(long versionId, List<PassiveNotable> notables);
}
