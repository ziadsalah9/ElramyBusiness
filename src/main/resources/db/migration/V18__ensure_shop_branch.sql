-- =============================================================
-- V18: a new install gets the shop's branch.
-- -------------------------------------------------------------
-- Sales, purchases and cash closings belong to a branch, and the app runs
-- in single-branch mode (V7). An existing database already has its branch,
-- so this only adds one when the table is empty (fresh install).
-- =============================================================

INSERT INTO branches (name, address, phone, active, created_at, updated_at)
SELECT 'فرع كوم حماة', NULL, NULL, TRUE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM branches);
