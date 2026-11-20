-- Catálogo versionado por patch do PoE 2 (card #4). Só uma versão PUBLISHED por vez.
-- Itens são únicos por versão ignorando caixa; relações nunca cruzam versões (FKs compostas).
-- Nomes só entram na forma canônica do domínio (CatalogNames): sem espaço nas pontas e com
-- qualquer sequência de espaço Unicode reduzida a um espaço. Assim, o banco nunca guarda um
-- dado que o domínio recusaria (chaos E1/E2 da #4).

-- Mesma classe de espaços da propriedade Unicode White_Space usada por (?U)\s no Java.
CREATE FUNCTION canonical_catalog_name(value TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT btrim(regexp_replace(
        value,
        '[\u0009-\u000D \u0085   -     　]+',
        ' ', 'g'), ' ')
$$;

CREATE TABLE catalog_version (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patch        VARCHAR(50)  NOT NULL,
    status       VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_catalog_version_single_published
    ON catalog_version (status) WHERE status = 'PUBLISHED';

CREATE TABLE character_class (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_version_id BIGINT       NOT NULL REFERENCES catalog_version (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL,
    UNIQUE (catalog_version_id, id)
);

CREATE TABLE ascendancy (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_version_id BIGINT       NOT NULL REFERENCES catalog_version (id) ON DELETE CASCADE,
    character_class_id BIGINT       NOT NULL,
    name               VARCHAR(120) NOT NULL,
    FOREIGN KEY (catalog_version_id, character_class_id)
        REFERENCES character_class (catalog_version_id, id) ON DELETE CASCADE
);

CREATE TABLE weapon_type (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_version_id BIGINT       NOT NULL REFERENCES catalog_version (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL,
    UNIQUE (catalog_version_id, id)
);

CREATE TABLE skill_gem (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_version_id BIGINT       NOT NULL REFERENCES catalog_version (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL,
    UNIQUE (catalog_version_id, id)
);

-- Tipos de arma com que a gema pode ser usada; nenhuma linha = sem exigência.
CREATE TABLE skill_gem_weapon_type (
    catalog_version_id BIGINT NOT NULL,
    skill_gem_id       BIGINT NOT NULL,
    weapon_type_id     BIGINT NOT NULL,
    PRIMARY KEY (skill_gem_id, weapon_type_id),
    FOREIGN KEY (catalog_version_id, skill_gem_id)
        REFERENCES skill_gem (catalog_version_id, id) ON DELETE CASCADE,
    FOREIGN KEY (catalog_version_id, weapon_type_id)
        REFERENCES weapon_type (catalog_version_id, id) ON DELETE CASCADE
);

CREATE TABLE support_gem (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_version_id BIGINT       NOT NULL REFERENCES catalog_version (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL
);

CREATE TABLE passive_notable (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_version_id BIGINT       NOT NULL REFERENCES catalog_version (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL,
    description        TEXT         NOT NULL DEFAULT ''
);

CREATE TABLE playstyle (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_version_id BIGINT       NOT NULL REFERENCES catalog_version (id) ON DELETE CASCADE,
    name               VARCHAR(120) NOT NULL
);

-- Forma canônica obrigatória: recusa vazio, espaço nas pontas e espaço interno duplicado/especial.
ALTER TABLE catalog_version ADD CONSTRAINT ck_catalog_version_patch
    CHECK (patch <> '' AND patch = canonical_catalog_name(patch));
ALTER TABLE character_class ADD CONSTRAINT ck_character_class_name
    CHECK (name <> '' AND name = canonical_catalog_name(name));
ALTER TABLE ascendancy ADD CONSTRAINT ck_ascendancy_name
    CHECK (name <> '' AND name = canonical_catalog_name(name));
ALTER TABLE weapon_type ADD CONSTRAINT ck_weapon_type_name
    CHECK (name <> '' AND name = canonical_catalog_name(name));
ALTER TABLE skill_gem ADD CONSTRAINT ck_skill_gem_name
    CHECK (name <> '' AND name = canonical_catalog_name(name));
ALTER TABLE support_gem ADD CONSTRAINT ck_support_gem_name
    CHECK (name <> '' AND name = canonical_catalog_name(name));
ALTER TABLE passive_notable ADD CONSTRAINT ck_passive_notable_name
    CHECK (name <> '' AND name = canonical_catalog_name(name));
ALTER TABLE playstyle ADD CONSTRAINT ck_playstyle_name
    CHECK (name <> '' AND name = canonical_catalog_name(name));

-- Unicidade por versão ignorando caixa. Como os nomes já são canônicos, lower(name) compara
-- exatamente como CatalogNames.normalize do domínio.
CREATE UNIQUE INDEX uq_character_class_name ON character_class (catalog_version_id, lower(name));
CREATE UNIQUE INDEX uq_ascendancy_name      ON ascendancy      (catalog_version_id, lower(name));
CREATE UNIQUE INDEX uq_weapon_type_name     ON weapon_type     (catalog_version_id, lower(name));
CREATE UNIQUE INDEX uq_skill_gem_name       ON skill_gem       (catalog_version_id, lower(name));
CREATE UNIQUE INDEX uq_support_gem_name     ON support_gem     (catalog_version_id, lower(name));
CREATE UNIQUE INDEX uq_passive_notable_name ON passive_notable (catalog_version_id, lower(name));
CREATE UNIQUE INDEX uq_playstyle_name       ON playstyle       (catalog_version_id, lower(name));

CREATE INDEX ix_ascendancy_class ON ascendancy (catalog_version_id, character_class_id);
CREATE INDEX ix_skill_gem_weapon_type_weapon ON skill_gem_weapon_type (catalog_version_id, weapon_type_id);
