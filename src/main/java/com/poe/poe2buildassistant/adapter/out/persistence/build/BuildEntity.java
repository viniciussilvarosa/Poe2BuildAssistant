package com.poe.poe2buildassistant.adapter.out.persistence.build;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.domain.Persistable;

/** Linha da tabela build. O id vem do domínio, então {@link Persistable} evita o SELECT antes do INSERT. */
@Entity
@Table(name = "build")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BuildEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "owner_id", length = 100)
    private String ownerId;

    @Column(name = "catalog_version_id", nullable = false)
    private Long catalogVersionId;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean fresh;

    BuildEntity(UUID id, String ownerId, Long catalogVersionId, String content, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.catalogVersionId = catalogVersionId;
        this.content = content;
        this.createdAt = createdAt;
        this.fresh = true;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }
}
