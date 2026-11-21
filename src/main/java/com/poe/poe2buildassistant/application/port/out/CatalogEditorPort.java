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

    /**
     * Apaga os notables da versão e grava estes no lugar, numa transação que trava a versão e
     * reconfere o status na hora da escrita (ela pode ter sido publicada desde a última leitura).
     *
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound versão inexistente
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable versão publicada
     */
    void replaceNotables(long versionId, List<PassiveNotable> notables);
}
