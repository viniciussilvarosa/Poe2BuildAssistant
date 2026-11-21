package com.poe.poe2buildassistant.application.port.in;

/** Importa os notables do export oficial da GGG para uma versão em rascunho do catálogo. */
public interface ImportPassiveTreeUseCase {

    /**
     * @return quantidade de notables gravados
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound versão inexistente
     * @throws com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable versão publicada
     * @throws com.poe.poe2buildassistant.domain.shared.PassiveTreeFormatChanged export em formato novo
     * @throws com.poe.poe2buildassistant.domain.shared.PassiveTreeUnavailable download falhou
     */
    int importInto(long versionId);
}
