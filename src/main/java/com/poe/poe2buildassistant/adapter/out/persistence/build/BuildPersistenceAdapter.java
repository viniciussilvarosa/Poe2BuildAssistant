package com.poe.poe2buildassistant.adapter.out.persistence.build;

import com.poe.poe2buildassistant.application.port.out.BuildRepositoryPort;
import com.poe.poe2buildassistant.domain.build.Build;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Persiste builds com o conteúdo em jsonb. Jackson e JPA ficam só neste adaptador. */
@Component
class BuildPersistenceAdapter implements BuildRepositoryPort {

    private final BuildJpaRepository repository;
    private final JsonMapper json = JsonMapper.builder().build();

    BuildPersistenceAdapter(BuildJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Build save(Build build) {
        // saveAndFlush: erros de integridade aparecem aqui, dentro do caso de uso, e não depois.
        repository.saveAndFlush(new BuildEntity(
                build.id(),
                build.ownerId().orElse(null),
                build.catalogVersionId(),
                json.writeValueAsString(BuildDocument.from(build.request(), build.content())),
                build.createdAt()));
        return build;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Build> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Build> findByOwner(String ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::toDomain).toList();
    }

    private Build toDomain(BuildEntity e) {
        BuildDocument doc = json.readValue(e.getContent(), BuildDocument.class);
        return new Build(
                e.getId(), Optional.ofNullable(e.getOwnerId()), e.getCatalogVersionId(), doc.toRequest(), doc.draft(), e.getCreatedAt());
    }
}
