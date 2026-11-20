package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface CatalogVersionRepository extends Repository<CatalogVersionEntity, Long> {

    Optional<CatalogVersionEntity> findByStatus(CatalogVersion.Status status);
}
