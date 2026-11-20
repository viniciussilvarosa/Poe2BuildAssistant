package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Colunas comuns a todo item do catálogo: id, versão e nome. */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
abstract class CatalogItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_version_id", nullable = false)
    private Long catalogVersionId;

    @Column(nullable = false, length = 120)
    private String name;
}
