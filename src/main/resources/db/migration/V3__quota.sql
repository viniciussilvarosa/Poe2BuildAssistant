-- Contador diário de gerações por solicitante (card #9). requester_key é "anon:<sha256>" ou
-- "user:<id>": nunca o IP puro. day é o dia UTC.
CREATE TABLE quota_counter (
    requester_key VARCHAR(100) NOT NULL,
    day           DATE         NOT NULL,
    count         INTEGER      NOT NULL CHECK (count >= 0),
    PRIMARY KEY (requester_key, day),
    CONSTRAINT ck_quota_counter_key CHECK (requester_key ~ '^(anon|user):.+$')
);

CREATE INDEX ix_quota_counter_day ON quota_counter (day);
