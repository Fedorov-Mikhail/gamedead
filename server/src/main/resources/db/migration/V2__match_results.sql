CREATE TABLE game.matches (
    id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING','WON','LOST','ABORTED','INTERRUPTED')),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    config_version INTEGER NOT NULL DEFAULT 3 CHECK (config_version = 3),
    desired_duration_seconds INTEGER NOT NULL CHECK (desired_duration_seconds BETWEEN 1 AND 1800),
    elapsed_ticks INTEGER NOT NULL DEFAULT 0 CHECK (elapsed_ticks BETWEEN 0 AND 36000),
    team_level INTEGER NOT NULL DEFAULT 1 CHECK (team_level BETWEEN 1 AND 10),
    team_xp INTEGER NOT NULL DEFAULT 0 CHECK (team_xp >= 0),
    team_kills INTEGER NOT NULL DEFAULT 0 CHECK (team_kills >= 0),
    CONSTRAINT ck_match_elapsed CHECK (elapsed_ticks <= desired_duration_seconds * 20),
    CONSTRAINT ck_match_finished CHECK (
        (status = 'RUNNING' AND finished_at IS NULL) OR
        (status <> 'RUNNING' AND finished_at IS NOT NULL AND finished_at >= started_at))
);

CREATE TABLE game.match_players (
    match_id UUID NOT NULL REFERENCES game.matches(id),
    account_id UUID NOT NULL REFERENCES game.accounts(id),
    display_name VARCHAR(24) NOT NULL CHECK (char_length(btrim(display_name)) BETWEEN 3 AND 24),
    kills INTEGER NOT NULL DEFAULT 0 CHECK (kills >= 0),
    damage INTEGER NOT NULL DEFAULT 0 CHECK (damage >= 0),
    level INTEGER NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 10),
    left_match BOOLEAN NOT NULL DEFAULT FALSE,
    upgrades JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(upgrades) = 'object'),
    account_xp BIGINT NOT NULL DEFAULT 0 CHECK (account_xp >= 0),
    PRIMARY KEY (match_id, account_id)
);
CREATE INDEX ix_match_players_account ON game.match_players(account_id);
CREATE INDEX ix_matches_finished ON game.matches(finished_at DESC) WHERE finished_at IS NOT NULL;
