-- =============================================================
-- V3: remove the duplicate unique constraint on product_stocks.
-- -------------------------------------------------------------
-- The entity used to declare an unnamed unique constraint, so
-- Hibernate generated a hash-named one
-- (ukmtsjosgtfjifp9jqby01ocd2l) alongside the natural
-- product_stocks_product_id_branch_id_key. The entity now names it
-- explicitly, so Hibernate no longer recreates the duplicate. This
-- migration cleans it up for databases that already had it.
-- Guarded so it is safe on fresh databases.
-- =============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'product_stocks'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'ukmtsjosgtfjifp9jqby01ocd2l'
              AND conrelid = 'public.product_stocks'::regclass
        ) THEN
            ALTER TABLE product_stocks
                DROP CONSTRAINT ukmtsjosgtfjifp9jqby01ocd2l;
        END IF;
    END IF;
END $$;