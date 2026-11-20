package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "weapon_type")
class WeaponTypeEntity extends CatalogItemEntity {}
