-- =============================================================
-- V4: optional secondary phone number for customers.
-- -------------------------------------------------------------
-- Not unique on purpose: family members often share a number.
-- Guarded so it is safe on fresh databases (table not created
-- yet) and where Hibernate (ddl-auto=update) already added it.
-- =============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'customers'
    ) THEN
        ALTER TABLE customers
            ADD COLUMN IF NOT EXISTS secondary_phone VARCHAR(20);
    END IF;
END $$;
