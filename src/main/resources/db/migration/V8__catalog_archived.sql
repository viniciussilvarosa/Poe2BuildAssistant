-- Administração do catálogo (card #17): versões publicadas viram ARCHIVED ao publicar a próxima,
-- e nunca são apagadas (builds salvas apontam para elas). Só pode haver um rascunho por vez.
ALTER TABLE catalog_version DROP CONSTRAINT catalog_version_status_check;
ALTER TABLE catalog_version
    ADD CONSTRAINT ck_catalog_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));

CREATE UNIQUE INDEX uq_catalog_version_single_draft
    ON catalog_version (status) WHERE status = 'DRAFT';
