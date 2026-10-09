-- =============================================================
-- V13: activity log - who did what, when, and what changed.
-- -------------------------------------------------------------
-- One row per successful change (written in the same transaction as
-- the change, so a rolled-back operation leaves no entry). The user's
-- name is copied in, so the log stays readable if the user is later
-- renamed or deactivated. `changes` holds a JSON list of
-- {field, before, after}. Additive only.
-- =============================================================

CREATE TABLE IF NOT EXISTS activity_logs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT,
    username       VARCHAR(255),
    user_full_name VARCHAR(255),
    action         VARCHAR(30)  NOT NULL,
    entity_type    VARCHAR(40)  NOT NULL,
    entity_id      BIGINT,
    entity_label   VARCHAR(255),
    description    TEXT         NOT NULL,
    changes        TEXT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_activity_logs_created ON activity_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_logs_user ON activity_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_entity ON activity_logs (entity_type, entity_id);
