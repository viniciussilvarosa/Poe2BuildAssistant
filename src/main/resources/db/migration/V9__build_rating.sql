-- Avaliação 👍/👎 das builds (card #16). Uma por avaliador e build: votar de novo substitui.
-- requester_key é a mesma chave da cota (anon:<hash do IP> ou user:<id>); o IP puro nunca é gravado.
-- Taxa positiva (critério de sucesso ≥ 70%):
--   SELECT avg(CASE value WHEN 'UP' THEN 1.0 ELSE 0.0 END) FROM build_rating;
CREATE TABLE build_rating (
    build_id      UUID                     NOT NULL REFERENCES build (id) ON DELETE CASCADE,
    requester_key VARCHAR(100)             NOT NULL,
    value         VARCHAR(4)               NOT NULL CHECK (value IN ('UP', 'DOWN')),
    rated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (build_id, requester_key),
    CONSTRAINT ck_build_rating_key CHECK (requester_key ~ '^anon:[0-9a-f]{64}$' OR requester_key ~ '^user:.+$')
);
