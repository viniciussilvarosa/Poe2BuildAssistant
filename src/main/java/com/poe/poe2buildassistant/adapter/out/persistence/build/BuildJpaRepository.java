package com.poe.poe2buildassistant.adapter.out.persistence.build;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BuildJpaRepository extends JpaRepository<BuildEntity, UUID> {

    List<BuildEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
