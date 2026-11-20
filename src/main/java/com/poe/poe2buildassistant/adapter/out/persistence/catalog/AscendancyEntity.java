package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "ascendancy")
@Getter
class AscendancyEntity extends CatalogItemEntity {

    @Column(name = "character_class_id", nullable = false)
    private Long characterClassId;
}
