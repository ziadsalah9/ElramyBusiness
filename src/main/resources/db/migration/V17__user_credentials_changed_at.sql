-- =============================================================
-- V17: revoke old logins after a password or username change.
-- -------------------------------------------------------------
-- A token issued before users.credentials_changed_at is rejected, so
-- changing a password (or renaming a user) logs out every old session
-- instead of leaving them valid until they expire (up to 24h). NULL =
-- no change recorded yet. Additive; idempotent.
-- =============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS credentials_changed_at TIMESTAMP;
