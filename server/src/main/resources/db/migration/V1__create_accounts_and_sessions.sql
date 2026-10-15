CREATE TABLE game.accounts (
    id UUID PRIMARY KEY,
    login VARCHAR(24) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uk_accounts_login UNIQUE (login),
    CONSTRAINT ck_accounts_login CHECK (login ~ '^[a-z0-9_]{3,24}$')
);

CREATE TABLE game.player_profiles (
    account_id UUID PRIMARY KEY REFERENCES game.accounts(id) ON DELETE CASCADE,
    display_name VARCHAR(24) NOT NULL,
    account_xp BIGINT NOT NULL DEFAULT 0 CHECK (account_xp >= 0),
    appearance_id VARCHAR(64) NOT NULL DEFAULT 'mage_default',
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_profile_name CHECK (char_length(btrim(display_name)) BETWEEN 3 AND 24)
);

CREATE TABLE game.auth_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES game.accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_auth_sessions_token UNIQUE (token_hash),
    CONSTRAINT ck_session_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_session_revocation CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);
CREATE INDEX ix_auth_sessions_account ON game.auth_sessions(account_id);
CREATE INDEX ix_auth_sessions_expiration ON game.auth_sessions(expires_at);
