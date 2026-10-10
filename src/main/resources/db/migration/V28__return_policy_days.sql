-- =============================================================
-- V28: return / exchange policy period printed on the receipt.
-- -------------------------------------------------------------
-- Days from the purchase date during which a customer may return or
-- exchange (unused, original packaging, with the receipt). 0 = the
-- receipt carries no policy. Idempotent.
-- =============================================================

ALTER TABLE store_settings ADD COLUMN IF NOT EXISTS return_policy_days INTEGER NOT NULL DEFAULT 7;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_store_settings_return_policy_days') THEN
        ALTER TABLE store_settings ADD CONSTRAINT ck_store_settings_return_policy_days
            CHECK (return_policy_days >= 0 AND return_policy_days <= 365);
    END IF;
END $$;
