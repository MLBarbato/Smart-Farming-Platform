CREATE TABLE refresh_token (
    id_refresh_token BIGSERIAL PRIMARY KEY,
    id_usuario BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMP NOT NULL,
    revogado BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_token_usuario
        FOREIGN KEY (id_usuario)
            REFERENCES usuario (id_usuario)
            ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_usuario
    ON refresh_token (id_usuario);
