-- Builds geradas e validadas (card #10). Toda build é salva; a de visitante tem owner_id NULL e
-- nunca aparece em histórico. A versão do catálogo usada fica registrada e não pode ser apagada
-- enquanto houver build apontando para ela.
CREATE TABLE build (
    id                 UUID PRIMARY KEY,
    owner_id           VARCHAR(100),
    catalog_version_id BIGINT                   NOT NULL REFERENCES catalog_version (id),
    content            JSONB                    NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_build_owner_created ON build (owner_id, created_at DESC) WHERE owner_id IS NOT NULL;
