package com.poe.poe2buildassistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.poe.poe2buildassistant.application.port.out.CatalogEditorPort;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotEditable;
import com.poe.poe2buildassistant.domain.shared.CatalogVersionNotFound;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImportPassiveTreeServiceTest {

    private static final List<PassiveNotable> FROM_SOURCE = List.of(
            new PassiveNotable("Her Final Bite", "Bow Attacks have Culling Strike"),
            new PassiveNotable("Cold Precision", "25% increased Cold Damage"),
            new PassiveNotable("Swift Flight", "8% increased Movement Speed"));

    /** Repositório em memória: só as operações que o import usa. */
    static final class FakeCatalogs implements CatalogEditorPort {
        final Map<Long, CatalogVersion> versions = new HashMap<>();
        final Map<Long, List<PassiveNotable>> notables = new HashMap<>();

        @Override
        public Optional<CatalogVersion> findVersion(long versionId) {
            return Optional.ofNullable(versions.get(versionId));
        }

        @Override
        public void replaceNotables(long versionId, List<PassiveNotable> replacement) {
            notables.put(versionId, new ArrayList<>(replacement));
        }

        // O import não usa as operações de curadoria (#17).
        @Override
        public Optional<CatalogVersion> findDraft() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CatalogVersion createDraftFromPublished(String patch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.poe.poe2buildassistant.domain.catalog.Catalog loadVersion(long versionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveItem(long versionId, com.poe.poe2buildassistant.domain.catalog.CatalogItem item) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeItem(long versionId, com.poe.poe2buildassistant.domain.catalog.CatalogItem.Type type, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CatalogVersion publish(long draftId) {
            throw new UnsupportedOperationException();
        }
    }

    private final FakeCatalogs catalogs = new FakeCatalogs();
    private final AtomicInteger fetches = new AtomicInteger();
    private final ImportPassiveTreeService service = new ImportPassiveTreeService(catalogs, () -> {
        fetches.incrementAndGet();
        return FROM_SOURCE;
    });

    @Test
    @DisplayName("grava notables na versão de catálogo informada, substituindo os anteriores")
    void substitui() {
        catalogs.versions.put(7L, new CatalogVersion(7, "1.0", CatalogVersion.Status.DRAFT));
        catalogs.notables.put(7L, List.of(new PassiveNotable("Antigo", "")));

        int imported = service.importInto(7L);

        assertThat(imported).isEqualTo(3);
        assertThat(catalogs.notables.get(7L)).containsExactlyElementsOf(FROM_SOURCE);
    }

    @Test
    @DisplayName("recusa importar em versão PUBLISHED")
    void recusaPublicada() {
        catalogs.versions.put(1L, new CatalogVersion(1, "0.5", CatalogVersion.Status.PUBLISHED));

        assertThatThrownBy(() -> service.importInto(1L)).isInstanceOf(CatalogVersionNotEditable.class);
        assertThat(catalogs.notables).doesNotContainKey(1L);
    }

    @Test
    @DisplayName("versão inexistente é recusada antes de baixar o export")
    void versaoInexistente() {
        assertThatThrownBy(() -> service.importInto(99L)).isInstanceOf(CatalogVersionNotFound.class);
        assertThat(fetches).hasValue(0);
    }

    @Test
    @DisplayName("versão publicada também não gasta download do repositório oficial")
    void publicadaNaoBaixa() {
        catalogs.versions.put(1L, new CatalogVersion(1, "0.5", CatalogVersion.Status.PUBLISHED));

        assertThatThrownBy(() -> service.importInto(1L)).isInstanceOf(CatalogVersionNotEditable.class);
        assertThat(fetches).hasValue(0);
    }
}
