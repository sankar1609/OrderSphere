-- Refresh tokens: long-lived, single-use, stored only as a SHA-256 hash. Each use rotates the token
-- (the old row is revoked and points at its replacement via replaced_by); all tokens descending
-- from one login share a family_id, so reuse of an already-rotated token (a sign it was stolen)
-- revokes the whole family at once.
CREATE TABLE refresh_tokens (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    family_id    VARCHAR(36) NOT NULL,
    expires_at   TIMESTAMP   NOT NULL,
    revoked_at   TIMESTAMP,
    replaced_by  BIGINT      REFERENCES refresh_tokens (id),
    created_at   TIMESTAMP   NOT NULL
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
