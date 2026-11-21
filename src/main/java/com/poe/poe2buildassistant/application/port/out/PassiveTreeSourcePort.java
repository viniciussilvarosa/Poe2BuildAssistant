package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import java.util.List;

/** Fonte oficial dos notables da árvore de passivas (export da GGG). */
public interface PassiveTreeSourcePort {

    /**
     * @return os notables da árvore principal; nunca vazio
     * @throws com.poe.poe2buildassistant.domain.shared.PassiveTreeFormatChanged se o formato mudou
     * @throws com.poe.poe2buildassistant.domain.shared.PassiveTreeUnavailable se o download falhou
     */
    List<PassiveNotable> fetchNotables();
}
