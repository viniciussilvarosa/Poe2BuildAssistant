package com.poe.poe2buildassistant.adapter.out.persistence.catalog;

import java.util.List;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** Consulta somente-leitura comum aos itens do catálogo: uma query por tabela, filtrada pela versão. */
@NoRepositoryBean
interface CatalogItemRepository<T extends CatalogItemEntity> extends Repository<T, Long> {

    List<T> findByCatalogVersionIdOrderByNameAsc(Long catalogVersionId);
}
