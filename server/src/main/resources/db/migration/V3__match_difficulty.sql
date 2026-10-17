ALTER TABLE game.matches
    ADD COLUMN difficulty VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
        CHECK (difficulty IN ('EASY','NORMAL','HARD'));
