package com.poe.poe2buildassistant.domain.build;

import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Conserta, sem nova chamada à IA, os erros de nome mais comuns nos suportes e notables (QA-02):
 *
 * <ul>
 *   <li>suporte sem o nível ("Biting Frost") vira o primeiro nível do catálogo ("Biting Frost I");
 *   <li>nome fora do catálogo é descartado, mas só se a lista continuar com o mínimo de itens
 *       distintos. Senão a lista fica como veio e o {@link BuildValidator} recusa com UNKNOWN_ITEM.
 * </ul>
 *
 * <p>Nunca cria nome: todo nome que sai daqui existe no catálogo ou já estava no rascunho. A regra
 * dos 100% continua no validador, que roda depois.
 */
public final class DraftRepair {

    /** Rascunho consertado e quantos nomes foram corrigidos ou descartados. */
    public record Result(GeneratedBuildDraft draft, int corrected, int dropped) {
        public boolean changed() {
            return corrected > 0 || dropped > 0;
        }
    }

    public Result repair(GeneratedBuildDraft draft, Catalog catalog) {
        Fixed supports = fix(draft.supports(), BuildValidator.MIN_SUPPORTS, name -> catalog.findSupportGem(name)
                .map(s -> s.name())
                .or(() -> catalog.findSupportGem(name + " I").map(s -> s.name())));
        Fixed notables = fix(draft.passiveTree().notables(), BuildValidator.MIN_NOTABLES, name -> catalog.findNotable(name)
                .map(n -> n.name()));
        if (supports.corrected + supports.dropped + notables.corrected + notables.dropped == 0) {
            return new Result(draft, 0, 0);
        }
        GeneratedBuildDraft repaired = new GeneratedBuildDraft(
                draft.summary(),
                draft.synergy(),
                draft.choiceReasons(),
                draft.characterClass(),
                draft.ascendancy(),
                draft.weaponType(),
                draft.mainSkill(),
                supports.names,
                new GeneratedBuildDraft.PassiveDirection(draft.passiveTree().direction(), notables.names),
                draft.gear(),
                draft.campaignTips());
        return new Result(repaired, supports.corrected + notables.corrected, supports.dropped + notables.dropped);
    }

    private record Fixed(List<String> names, int corrected, int dropped) {}

    private static Fixed fix(List<String> names, int min, Function<String, Optional<String>> resolve) {
        List<String> kept = new ArrayList<>();
        Set<String> distinct = new HashSet<>();
        int corrected = 0;
        int unknown = 0;
        for (String name : names) {
            Optional<String> found = resolve.apply(name);
            if (found.isEmpty()) {
                unknown++;
                continue;
            }
            if (!CatalogNames.normalize(found.get()).equals(CatalogNames.normalize(name))) {
                corrected++;
            }
            kept.add(found.get());
            distinct.add(CatalogNames.normalize(found.get()));
        }
        if (unknown > 0 && distinct.size() < min) {
            return new Fixed(names, 0, 0); // não dá para salvar: o validador recusa e a IA recebe o feedback
        }
        // Corrigido, mas sem descartes: mantém os nomes do catálogo; repetições continuam para o validador.
        return corrected == 0 && unknown == 0 ? new Fixed(names, 0, 0) : new Fixed(kept, corrected, unknown);
    }
}
