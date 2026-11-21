-- Contas do login Google (card #14). Um registro por sub do Google; e-mail e nome são dados
-- pessoais e não saem para logs. As builds apontam para o usuário pelo id (build.owner_id).
CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    google_sub    VARCHAR(255)             NOT NULL,
    email         VARCHAR(320)             NOT NULL,
    display_name  VARCHAR(200)             NOT NULL,
    role          VARCHAR(20)              NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_google_sub UNIQUE (google_sub)
);
