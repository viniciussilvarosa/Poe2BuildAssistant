package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.GetCatalogOptionsUseCase;
import com.poe.poe2buildassistant.application.port.out.CatalogRepositoryPort;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class GetCatalogOptionsService implements GetCatalogOptionsUseCase {

    /** Ignora caixa e acento: "À distância" fica no A, como o usuário espera ler. */
    private static final Comparator<String> ALPHABETICAL = alphabetical();

    private final CatalogRepositoryPort catalogs;

    public GetCatalogOptionsService(CatalogRepositoryPort catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    public CatalogOptions get() {
        Catalog catalog = catalogs.loadPublished();
        List<ClassOption> classes = sorted(catalog.classes(), c -> c.name()).stream()
                .map(c -> new ClassOption(c.name(), names(catalog.ascendanciesOf(c), a -> a.name())))
                .toList();
        List<SkillGemOption> gems = sorted(catalog.skillGems(), g -> g.name()).stream()
                .map(g -> new SkillGemOption(g.name(), g.weaponTypes().stream().sorted(ALPHABETICAL).toList()))
                .toList();
        return new CatalogOptions(
                catalog.version().patch(),
                classes,
                names(catalog.weaponTypes(), w -> w.name()),
                gems,
                names(catalog.playstyles(), p -> p.name()));
    }

    private static <T> List<T> sorted(List<T> items, Function<T, String> name) {
        return items.stream().sorted(Comparator.comparing(name, ALPHABETICAL)).toList();
    }

    private static <T> List<String> names(List<T> items, Function<T, String> name) {
        return items.stream().map(name).sorted(ALPHABETICAL).toList();
    }

    private static Comparator<String> alphabetical() {
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        collator.setStrength(Collator.PRIMARY);
        // Desempate estável para nomes que só diferem em caixa ou acento.
        return Comparator.<String, String>comparing(s -> s, collator::compare).thenComparing(Comparator.naturalOrder());
    }
}
