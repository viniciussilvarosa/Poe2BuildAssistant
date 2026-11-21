package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import java.util.List;
import java.util.Optional;

/**
 * Escrita no catálogo versionado, só em rascunho. Separada de {@link CatalogRepositoryPort}, que é
 * só leitura e usada a cada geração. Toda escrita trava a versão e reconfere que é rascunho.
 */
public interface CatalogEditorPort {

    Optional<CatalogVersion> findVersion(long versionId);

    Optional<CatalogVersion> findDraft();

    /**
     * Cria um rascunho com cópia de todos os itens da versão publicada (inclusive notables).
     *
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogNotPublished se não há versão publicada
     */
    CatalogVersion createDraftFromPublished(String patch);

    /** @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound versão inexistente */
    Catalog loadVersion(long versionId);

    /** Inclui o item no rascunho, ou atualiza o de mesmo tipo e nome. Trava e reconfere o rascunho. */
    void saveItem(long versionId, CatalogItem item);

    void removeItem(long versionId, CatalogItem.Type type, String name);

    /**
     * Numa transação: arquiva a versão publicada e publica o rascunho.
     *
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable se não é rascunho
     */
    CatalogVersion publish(long draftId);

    /**
     * Apaga os notables da versão e grava estes no lugar, numa transação que trava a versão e
     * reconfere o status na hora da escrita (ela pode ter sido publicada desde a última leitura).
     *
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound versão inexistente
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable versão publicada
     */
    void replaceNotables(long versionId, List<PassiveNotable> notables);
}
