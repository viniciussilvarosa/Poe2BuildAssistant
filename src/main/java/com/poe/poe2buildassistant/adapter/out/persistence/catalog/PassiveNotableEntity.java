package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "passive_notable")
@Getter
class PassiveNotableEntity extends CatalogItemEntity {

    @Column(nullable = false, columnDefinition = "text")
    private String description;
}
