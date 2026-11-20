package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import java.util.List;
import org.springframework.data.repository.Repository;

interface SkillGemWeaponTypeRepository extends Repository<SkillGemWeaponTypeEntity, SkillGemWeaponTypeEntity.Key> {

    List<SkillGemWeaponTypeEntity> findByCatalogVersionId(Long catalogVersionId);
}
