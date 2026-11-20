package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tipo de arma com que uma gema pode ser usada (tabela de junção, com a versão explícita). */
@Entity
@Table(name = "skill_gem_weapon_type")
@IdClass(SkillGemWeaponTypeEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SkillGemWeaponTypeEntity {

    record Key(Long skillGemId, Long weaponTypeId) implements Serializable {}

    @Id
    @Column(name = "skill_gem_id")
    private Long skillGemId;

    @Id
    @Column(name = "weapon_type_id")
    private Long weaponTypeId;

    @Column(name = "catalog_version_id", nullable = false)
    private Long catalogVersionId;
}
