-- RSA key pairs auth-service signs JWTs with. The newest active key signs; every row's public key
-- is published at /auth/.well-known/jwks.json so tokens signed with an older key keep verifying
-- until they expire. Generated on first start (unless a key is supplied via JWT_PRIVATE_KEY /
-- JWT_PUBLIC_KEY), so tokens survive restarts.
CREATE TABLE signing_keys (
    kid          VARCHAR(64) PRIMARY KEY,
    public_key   TEXT        NOT NULL,
    private_key  TEXT        NOT NULL,
    active       BOOLEAN     NOT NULL,
    created_at   TIMESTAMP   NOT NULL
);
