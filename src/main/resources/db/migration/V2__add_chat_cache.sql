CREATE TABLE chat_cache (
    id          BIGSERIAL    PRIMARY KEY,
    cache_key   VARCHAR(160) NOT NULL UNIQUE,
    intent      VARCHAR(50)  NOT NULL,
    message     TEXT         NOT NULL,
    date_bucket DATE,
    payload     JSONB        NOT NULL,
    hit_count   INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_chat_cache_expires_at ON chat_cache(expires_at);
