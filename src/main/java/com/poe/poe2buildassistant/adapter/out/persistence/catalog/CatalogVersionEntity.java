package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "catalog_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CatalogVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String patch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogVersion.Status status;

    @Column(name = "published_at")
    private Instant publishedAt;
}
