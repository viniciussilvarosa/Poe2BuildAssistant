package com.poe.poe2buildassistant.adapter.in.web.admin;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUser;
import com.poe.poe2buildassistant.application.port.in.ImportPassiveTreeUseCase;
import com.poe.poe2buildassistant.application.port.in.ManageCatalogUseCase;
import com.poe.poe2buildassistant.application.port.in.PublishCatalogVersionUseCase;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogItem;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.shared.InvalidCatalogChange;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * API de curadoria do catálogo. O SecurityConfig exige {@code ROLE_ADMIN} e CSRF em todo
 * {@code /api/admin/**}; o id do admin vai para a trilha de auditoria (logs), nunca e-mail.
 */
@RestController
@RequestMapping("/api/admin/catalog/drafts")
class AdminCatalogController {

    /** Segmento da URL → tipo de item. Notables não entram: só pelo import da árvore oficial. */
    private static final Map<String, CatalogItem.Type> TYPES = Map.of(
            "classes", CatalogItem.Type.CLASS,
            "ascendancies", CatalogItem.Type.ASCENDANCY,
            "weapon-types", CatalogItem.Type.WEAPON_TYPE,
            "skill-gems", CatalogItem.Type.SKILL_GEM,
            "support-gems", CatalogItem.Type.SUPPORT_GEM,
            "playstyles", CatalogItem.Type.PLAYSTYLE);

    record CreateDraft(String patch) {}

    record ItemBody(String name, String characterClass, List<String> weaponTypes) {}

    record VersionView(long id, String patch, CatalogVersion.Status status) {
        static VersionView from(CatalogVersion v) {
            return new VersionView(v.id(), v.patch(), v.status());
        }
    }

    record ClassView(String name, List<String> ascendancies) {}

    record SkillGemView(String name, List<String> weaponTypes) {}

    record CatalogView(
            VersionView version,
            List<ClassView> classes,
            List<String> weaponTypes,
            List<SkillGemView> skillGems,
            List<String> supportGems,
            List<String> playstyles,
            int notables) {

        static CatalogView from(Catalog c) {
            return new CatalogView(
                    VersionView.from(c.version()),
                    c.classes().stream()
                            .map(cl -> new ClassView(cl.name(), c.ascendanciesOf(cl).stream().map(a -> a.name()).toList()))
                            .toList(),
                    c.weaponTypes().stream().map(w -> w.name()).toList(),
                    c.skillGems().stream().map(g -> new SkillGemView(g.name(), g.weaponTypes())).toList(),
                    c.supportGems().stream().map(g -> g.name()).toList(),
                    c.playstyles().stream().map(p -> p.name()).toList(),
                    c.notables().size());
        }
    }

    record ImportResult(int notables) {}

    private final ManageCatalogUseCase manage;
    private final PublishCatalogVersionUseCase publish;
    private final ImportPassiveTreeUseCase importTree;

    AdminCatalogController(ManageCatalogUseCase manage, PublishCatalogVersionUseCase publish, ImportPassiveTreeUseCase importTree) {
        this.manage = manage;
        this.publish = publish;
        this.importTree = importTree;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VersionView createDraft(@RequestBody CreateDraft body, @AuthenticationPrincipal SessionUser admin) {
        return VersionView.from(manage.createDraft(body.patch(), admin.userId()));
    }

    @GetMapping("/current")
    ResponseEntity<VersionView> currentDraft() {
        return ResponseEntity.of(manage.currentDraft().map(VersionView::from));
    }

    @GetMapping("/{id}")
    CatalogView draft(@PathVariable long id) {
        return CatalogView.from(manage.draftCatalog(id));
    }

    @PutMapping("/{id}/{itemType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveItem(
            @PathVariable long id, @PathVariable String itemType, @RequestBody ItemBody body, @AuthenticationPrincipal SessionUser admin) {
        CatalogItem item;
        try {
            item = new CatalogItem(type(itemType), body.name(), body.characterClass(), body.weaponTypes());
        } catch (IllegalArgumentException e) {
            throw new InvalidCatalogChange(e.getMessage());
        }
        manage.saveItem(id, item, admin.userId());
    }

    @DeleteMapping("/{id}/{itemType}/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeItem(
            @PathVariable long id, @PathVariable String itemType, @PathVariable String name, @AuthenticationPrincipal SessionUser admin) {
        manage.removeItem(id, type(itemType), name, admin.userId());
    }

    @PostMapping("/{id}/publish")
    VersionView publish(@PathVariable long id, @AuthenticationPrincipal SessionUser admin) {
        return VersionView.from(publish.publish(id, admin.userId()));
    }

    @PostMapping("/{id}/passive-tree/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ImportResult importPassiveTree(@PathVariable long id) {
        return new ImportResult(importTree.importInto(id));
    }

    private static CatalogItem.Type type(String segment) {
        CatalogItem.Type type = TYPES.get(segment);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de item desconhecido: " + segment);
        }
        return type;
    }
}
