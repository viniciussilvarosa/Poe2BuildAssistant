package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.BuildViolation;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import java.util.List;

/** Geração de um rascunho de build por um LLM. O rascunho ainda precisa passar pelo BuildValidator. */
public interface BuildGeneratorPort {

    /**
     * @param slice itens do catálogo que a IA pode citar
     * @param feedback violações da tentativa anterior (vazio na primeira tentativa)
     * @throws com.poe.poe2buildassistant.domain.shared.ProviderUnavailable provedor fora do ar, lento ou sem cota
     * @throws com.poe.poe2buildassistant.domain.shared.GenerationFailed resposta inutilizável
     */
    GeneratedBuildDraft generate(BuildRequest request, CatalogSlice slice, List<BuildViolation> feedback);
}
